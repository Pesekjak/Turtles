package dev.pesek.turtles.blocks;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.ForOverride;
import dev.pesek.turtles.Turtles;
import dev.pesek.turtles.TurtlesKeys;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.computer.ComputerManager;
import dev.pesek.turtles.computer.PhysicalComputer;
import dev.pesek.turtles.util.DialogUtils;
import dev.pesek.turtles.util.PersistentDataContainerFactory;
import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.block.interfaces.BlockBreakRebarBlockHandler;
import io.github.pylonmc.rebar.block.interfaces.InteractRebarBlockHandler;
import io.github.pylonmc.rebar.block.interfaces.NoVanillaInventoryRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.UnloadRebarBlockHandler;
import io.github.pylonmc.rebar.datatypes.RebarSerializers;
import io.github.pylonmc.rebar.event.RebarBlockUnloadEvent;
import io.github.pylonmc.rebar.event.api.annotation.MultiHandler;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents a block bound to a {@link PhysicalComputer}.
 *
 * @param <T> type of the computer
 * @param <B> type of the computer block
 */
public abstract class ComputerBlock<T extends PhysicalComputer<T, B>, B extends ComputerBlock<T, B>>
        extends RebarBlock
        implements InteractRebarBlockHandler, UnloadRebarBlockHandler,
            NoVanillaInventoryRebarBlock, BlockBreakRebarBlockHandler {

    private static final NamespacedKey COMPUTER_BLOCK_DATA = TurtlesKeys.key("computer_block_data");
    private static final NamespacedKey COMPUTER_DATA = TurtlesKeys.key("computer_data");
    private static final NamespacedKey SHUTDOWN_MESSAGE = TurtlesKeys.key("shutdown_message");
    private static final NamespacedKey SHOULD_STARTUP = TurtlesKeys.key("should_startup");
    private static final NamespacedKey OWNER_ID = TurtlesKeys.key("owner_id");

    private sealed interface State<T extends Computer> {
        /**
         * Represents a computer block that has a running computer assigned to it.
         *
         * @param computer assigned running computer
         * @param <T> computer type
         */
        record Running<T extends Computer>(T computer) implements State<T> {
            public Running {
                Preconditions.checkNotNull(computer, "computer");
            }
        }

        /**
         * Represents a computer block which computer has been turned off.
         *
         * @param computerData serialized data of the turned off computer
         * @param message message to display on the shutdown screen
         * @param failed whether the shutdown was initiated by player or the computer failed
         *               to start up. If {@code true}, the computer will try to restart itself
         *               on the next load of the computer block
         * @param <T> computer type
         */
        record Shutdown<T extends Computer>(@Nullable PersistentDataContainer computerData,
                                            @Nullable String message,
                                            boolean failed)
                implements State<T> {
        }
    }

    private final @Nullable UUID ownerId;
    private State<T> state;

    public ComputerBlock(@NotNull Block block, @NotNull BlockCreateContext context) throws IOException {
        super(block, context);
        // changed when block is placed by player
        ownerId = context.getPlayer() != null ? context.getPlayer().getUniqueId() : null;
        PersistentDataContainer computerBlockData = null;
        ItemStack itemStack = context.getItem();
        if (itemStack != null) {
            computerBlockData = itemStack.getPersistentDataContainer().get(COMPUTER_BLOCK_DATA, PersistentDataType.TAG_CONTAINER);
        }
        restoreState(computerBlockData);
    }

    public ComputerBlock(@NotNull Block block, @NotNull PersistentDataContainer pdc) throws IOException {
        super(block, pdc);
        PersistentDataContainer computerBlockData = pdc.get(COMPUTER_BLOCK_DATA, PersistentDataType.TAG_CONTAINER);
        // on block load, read the saved owner
        ownerId = computerBlockData != null ? computerBlockData.get(OWNER_ID, RebarSerializers.UUID) : null;
        restoreState(computerBlockData);
    }

    private void requestComputer(@Nullable PersistentDataContainer computerData) throws IOException {
        switch (createNewComputer(ownerId, computerData)) {
            case ComputerManager.ComputerRequest.Success(T computer) -> state = new State.Running<>(computer);
            case ComputerManager.ComputerRequest.Fail(String reason) -> state = new State.Shutdown<>(
                    computerData, reason, true);
        }
    }

    private void restoreState(@Nullable PersistentDataContainer pdc) throws IOException {
        boolean shouldStart = pdc == null
                || pdc.getOrDefault(SHOULD_STARTUP, PersistentDataType.BOOLEAN, true);
        PersistentDataContainer computerData = pdc != null
                ? pdc.get(COMPUTER_DATA, PersistentDataType.TAG_CONTAINER)
                : null;

        if (shouldStart) {
            requestComputer(computerData);
        } else {
            String message = pdc.get(SHUTDOWN_MESSAGE, PersistentDataType.STRING);
            state = new State.Shutdown<>(computerData, message, false);
        }
    }

    private PersistentDataContainer serializeState(boolean preserveState) throws IOException {
        PersistentDataContainer pdc = PersistentDataContainerFactory.empty();
        if (ownerId != null) {
            pdc.set(OWNER_ID, RebarSerializers.UUID, ownerId);
        }
        switch (state) {
            case State.Running(T computer) -> {
                PersistentDataContainer computerData = PersistentDataContainerFactory.empty();
                computer.serialize(computerData, preserveState);
                pdc.set(COMPUTER_DATA, PersistentDataType.TAG_CONTAINER, computerData);
                pdc.set(SHOULD_STARTUP, PersistentDataType.BOOLEAN, true);
            }
            case State.Shutdown(PersistentDataContainer computerData, String message, boolean failed) -> {
                if (computerData != null) {
                    pdc.set(COMPUTER_DATA, PersistentDataType.TAG_CONTAINER, computerData);
                }
                if (message != null) {
                    pdc.set(SHUTDOWN_MESSAGE, PersistentDataType.STRING, message);
                }
                pdc.set(SHOULD_STARTUP, PersistentDataType.BOOLEAN, failed);
            }
        }
        return pdc;
    }

    private Optional<State.Running<T>> toRunningState() throws IOException {
        if (!(state instanceof State.Shutdown<T>(PersistentDataContainer computerData, String _, boolean _))) {
            return Optional.of((State.Running<T>) state);
        }
        requestComputer(computerData);
        return state instanceof State.Running<T> running ? Optional.of(running) : Optional.empty();
    }

    private State.Shutdown<T> toShutdownState(@Nullable String message, boolean failed, boolean preserveState) throws Exception {
        if (!(state instanceof State.Running<T>(T computer))) {
            return (State.Shutdown<T>) state;
        }
        PersistentDataContainer computerData = PersistentDataContainerFactory.empty();
        computer.serializeAndClose(computerData, preserveState);
        state = new State.Shutdown<>(computerData, message, failed);
        return (State.Shutdown<T>) state;
    }

    protected final Optional<T> getComputer() {
        return state instanceof State.Running(T computer) ? Optional.of(computer) : Optional.empty();
    }

    protected abstract ComputerManager.ComputerRequest<T> createNewComputer(
            @Nullable UUID ownerId, @Nullable PersistentDataContainer pdc) throws IOException;

    private boolean canShutdown(T requested) {
        if (!(state instanceof State.Running<T>(T computer))) {
            return false;
        }
        return requested.equals(computer) && !requested.isClosed();
    }

    public final void reboot(T toReboot) {
        Preconditions.checkNotNull(toReboot, "toReboot");
        if (!canShutdown(toReboot)) {
            return;
        }
        try {
            onReboot(toReboot);
            toShutdownState("Restarting...", true, false);
            toRunningState();
        } catch (Exception e) {
            Location location = getBlock().getLocation();
            Turtles.logger().error("Failed to reboot computer at: {}, {}, {}",
                    location.getBlockX(), location.getBlockY(), location.getBlockZ(), e);
        }
    }

    @ForOverride
    @MustBeInvokedByOverriders
    protected void onReboot(T computer) {
        playSound(Key.key("block.redstone_torch.burnout"), 1, 1.5f);
    }

    public final void shutdown(T toShutdown, @Nullable String reason) {
        Preconditions.checkNotNull(toShutdown, "toShutdown");
        if (!canShutdown(toShutdown)) {
            return;
        }
        try {
            onShutdown(toShutdown, reason);
            toShutdownState(reason, false, false);
        } catch (Exception e) {
            Location location = getBlock().getLocation();
            Turtles.logger().error("Failed to shutdown computer at: {}, {}, {}",
                    location.getBlockX(), location.getBlockY(), location.getBlockZ(), e);
        }
    }

    @ForOverride
    @MustBeInvokedByOverriders
    protected void onShutdown(T computer, String reason) {
        playSound(Key.key("entity.iron_golem.hurt"), 0.7f, 0.5f);
    }

    public final void destroy(T toDestroy) {
        Preconditions.checkNotNull(toDestroy, "toDestroy");
        if (!canShutdown(toDestroy)) {
            return;
        }
        try {
            onDestroy(toDestroy);
            BlockStorage.breakBlock(this, new BlockBreakContext.PluginBreak(getBlock(), false, true));
        } catch (Exception e) {
            Location location = getBlock().getLocation().toCenterLocation();
            Turtles.logger().error("Failed to destroy computer at: {}, {}, {}",
                    location.getBlockX(), location.getBlockY(), location.getBlockZ(), e);
        }
    }

    @ForOverride
    @MustBeInvokedByOverriders
    protected void onDestroy(T computer) {
        Block block = getBlock();
        World world = block.getWorld();
        Location location = block.getLocation().toCenterLocation();
        world.playEffect(location, Effect.DESTROY_BLOCK, block.getBlockData());
        world.spawnParticle(Particle.EXPLOSION, location, 1);
        world.dropItemNaturally(location, getAsNewDrop());
        playSound(Key.key("entity.generic.explode"), 0.9f, 1.7f);
    }

    /**
     * Called when computer is destroyed and
     * drops as a newly crafted version with no data stored.
     *
     * @return new version of computer
     */
    protected abstract ItemStack getAsNewDrop();

    public void playSound(Key sound, float volume, float pitch) {
        Block block = getBlock();
        World world = block.getWorld();
        Location location = block.getLocation().toCenterLocation();
        //noinspection UnstableApiUsage
        world.playSound(Sound.sound(sound, Sound.Source.PLAYER, volume, pitch), location);
    }

    @Override
    @MustBeInvokedByOverriders
    public @Nullable ItemStack getDropItem(@NotNull BlockBreakContext context) {
        ItemStack got = super.getDropItem(context);
        if (got == null) {
            return null;
        }
        got.editPersistentDataContainer(pdc -> {
            PersistentDataContainer computerBlockData = null;
            try {
                computerBlockData = serializeState(true);
            } catch (IOException e) {
                Turtles.logger().error("Failed to serialize computer data to item", e);
            }
            if (computerBlockData != null) {
                pdc.set(COMPUTER_BLOCK_DATA, PersistentDataType.TAG_CONTAINER, computerBlockData);
            }
        });
        return got;
    }

    @Override
    @MustBeInvokedByOverriders
    @MultiHandler(priorities = { EventPriority.NORMAL, EventPriority.MONITOR }, ignoreCancelled = true)
    public void onInteractedWith(@NotNull PlayerInteractEvent event, @NotNull EventPriority priority) {
        if (event.getPlayer().isSneaking()
                || event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.useInteractedBlock() == Event.Result.DENY
        ) {
            return;
        }

        if (priority == EventPriority.NORMAL) {
            event.setUseItemInHand(Event.Result.DENY);
            return;
        }

        event.setUseInteractedBlock(Event.Result.DENY);

        Player player = event.getPlayer();

        if (state instanceof State.Running<T>(T computer)) {
            computer.open(player);
        } else if (state instanceof State.Shutdown<T>(PersistentDataContainer _, String message, boolean _)) {
            openShutdownDialog(player, message);
        }
    }

    private void openShutdownDialog(Player player, @Nullable String message) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("This computer is turned off but "
                + "you can try to turn it back on.")));
        if (message != null) {
            body.add(DialogBody.plainMessage(Component.text("Shutdown Message: ")
                    .append(Component.text(message, NamedTextColor.RED))));
        }
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogUtils.dialogBase(Component.text("This computer is turned off", NamedTextColor.YELLOW),
                        body, null))
                // TODO use multi-action from dialog utils
                .type(DialogType.confirmation(
                        DialogUtils.actionButton("Turn On", (_, _) -> {
                            try {
                                toRunningState().ifPresentOrElse(
                                        running -> running.computer().open(player),
                                        () -> openShutdownDialog(player, ((State.Shutdown<T>) state).message)
                                );
                            } catch (IOException e) {
                                Turtles.logger().error("Failed to turn on computer from shutdown state", e);
                            }
                        }),
                        DialogUtils.actionButton("Cancel", (_, _) ->
                                player.closeDialog())
                ))
        );
        player.showDialog(dialog);
    }

    @Override
    @MustBeInvokedByOverriders
    @MultiHandler(priorities = EventPriority.MONITOR)
    public void onUnload(@NotNull RebarBlockUnloadEvent event, @NotNull EventPriority priority) {
        Location location = event.getBlock().getLocation();
        try {
            toShutdownState("Unloaded", true, true);
        } catch (Exception e) {
            Turtles.logger().error("Failed to unload computer at: {}, {}, {}",
                    location.getBlockX(), location.getBlockY(), location.getBlockZ(), e);
        }
    }

    @Override
    public void onBlockBreak(@NotNull List<ItemStack> drops, @NotNull BlockBreakContext context) {
        Location location = context.getBlock().getLocation();
        try {
            toShutdownState("Block destroyed", true, true);
        } catch (Exception e) {
            Turtles.logger().error("Failed to unload computer after break at: {}, {}, {}",
                    location.getBlockX(), location.getBlockY(), location.getBlockZ(), e);
        }
    }

    @Override
    @MustBeInvokedByOverriders
    public final void write(@NotNull PersistentDataContainer pdc) {
        Location location = getBlock().getLocation();
        try {
            PersistentDataContainer computerBlockData = serializeState(true);
            pdc.set(COMPUTER_BLOCK_DATA, PersistentDataType.TAG_CONTAINER, computerBlockData);
        } catch (IOException e) {
            Turtles.logger().error("Failed to serialize computer at: {}, {}, {}",
                    location.getBlockX(), location.getBlockY(), location.getBlockZ(), e);
        }
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        WailaDisplay display = WailaDisplay.of(this, player);
        getComputer().ifPresentOrElse(
                computer -> {
                    String name = computer.getName();
                    display.add(Component.text(name, NamedTextColor.GRAY).style(Style.style(TextDecoration.ITALIC)));
                },
                () -> display.add(Component.text("Turned Off", NamedTextColor.RED))
        );
        return display;
    }

}
