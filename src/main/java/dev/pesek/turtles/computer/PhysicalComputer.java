package dev.pesek.turtles.computer;

import com.google.common.base.Preconditions;
import dev.pesek.turtles.blocks.ComputerBlock;
import dev.pesek.turtles.util.MainThread;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Represents a computer that exists in a world bound to a {@link ComputerBlock}.
 *
 * @param <T> type of the computer
 * @param <B> type of the computer block
 */
// TODO turtles
public class PhysicalComputer<T extends PhysicalComputer<T, B>, B extends ComputerBlock<T, B>> extends Computer {

    private final Supplier<? extends B> blockSupplier;

    protected final EnvVar<Vector> location;
    protected final EnvVar<String> world;

    public PhysicalComputer(B computerBlock) {
        Preconditions.checkNotNull(computerBlock, "computerBlock");
        this(() -> computerBlock);
    }

    public PhysicalComputer(Supplier<? extends B> blockSupplier) {
        this.blockSupplier = Preconditions.checkNotNull(blockSupplier, "blockSupplier");
        location = dynamicEnvVar("location", () -> MainThread.run(() ->
                getBlock().getBlock().getLocation().toVector()).join());
        world = dynamicEnvVar("world", () -> MainThread.run(() ->
                getBlock().getBlock().getLocation().getWorld().getName()).join());
    }

    public final Vector getLocation() {
        return location.get();
    }

    public final String getWorld() {
        return world.get();
    }

    /**
     * @apiNote must be called on the main thread
     */
    public final B getBlock() {
        MainThread.ensure();
        return Preconditions.checkNotNull(blockSupplier.get(), "computerBlock");
    }

    @Override
    public void reboot() {
        MainThread.run(() -> getBlock().reboot(self()));
    }

    @Override
    public void shutdown(@Nullable String reason) {
        MainThread.run(() -> getBlock().shutdown(self(), reason));
    }

    @Override
    public void destroy() {
        MainThread.run(() -> getBlock().destroy(self()));
    }

    private T self() {
        //noinspection unchecked
        return (T) this;
    }

}
