package dev.pesek.turtles.computer;

import com.google.common.base.Preconditions;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.ThreadSafe;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.pesek.turtles.Turtles;
import dev.pesek.turtles.TurtlesKeys;
import dev.pesek.turtles.computer.commands.*;
import dev.pesek.turtles.computer.event.ComputerCommandsInitEvent;
import dev.pesek.turtles.computer.event.ComputerFilesInitEvent;
import dev.pesek.turtles.computer.script.ScriptExtension;
import dev.pesek.turtles.computer.script.ScriptRunner;
import dev.pesek.turtles.util.*;
import io.github.pylonmc.rebar.datatypes.RebarSerializers;
import io.jactl.CompileError;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.UnmodifiableView;

import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Represents a base computer implementation.
 * <p>
 * This computer implementation is fully virtual (does not exist in the world).
 * For example of computer that exists in the world see {@link PhysicalComputer}.
 * <p>
 * This base implementation contains file system, basic commands to manage the file system, and
 * Lua program interpreter.
 * <p>
 * To create and properly initialize any computer instance, use
 * {@link ComputerManager#create(UUID, Supplier, PersistentDataContainer)}.
 * <p>
 * All methods in this class are thread safe.
 */
@ThreadSafe
// TODO modules/upgrades
// TODO peripherals
// TODO execute boot script on start
// TODO run could specify and concat multiple scripts
public abstract class Computer {

    /**
     * Executor of computer commands.
     *
     * @param computer computer used for the command execution
     * @param player player that executed the command, can be {@code null} if
     *               the command was executed programmatically
     * @param <T> computer type
     */
    public record Executor<T extends Computer>(T computer, @Nullable Player player) {
        public Executor {
            Preconditions.checkNotNull(computer, "computer");
        }
    }

    /**
     * Path to the home directory.
     * <p>
     * This directory is used as a default working directory.
     */
    public static final String HOME_PATH = "/home";
    public static final String DEFAULT_COMPUTER_NAME = "Computer";

    private final FileSystem fileSystem;
    private final AtomicReference<Path> workingDirectory;

    private final CommandDispatcher<Executor<Computer>> dispatcher;
    private final List<ComputerCommand.Info> registeredCommands = new CopyOnWriteArrayList<>();

    private final ScriptRunner scriptRunner;
    private volatile ScriptRunner.@Nullable RunningScript runningScript = null;

    private final Screen screen;

    private final Set<EnvVar<?>> environmentVariables = new CopyOnWriteArraySet<>();

    private final Set<Player> activeViewers = new CopyOnWriteArraySet<>();

    protected final EnvVar<String> name = envVar("name", DEFAULT_COMPUTER_NAME, true, StringArgumentType.greedyString(),
            PersistentDataType.STRING, this::refreshScreen);
    protected final EnvVar<UUID> uid = dynamicEnvVar("uid", new Supplier<>() {
        // each Computer instance is assigned a random uid that cannot be changed
        // this uid is used to manage this instance lifecycle
        private final UUID uid = UUID.randomUUID();

        @Override
        public UUID get() {
            return uid;
        }
    });
    protected final EnvVar<UUID> ownerId = envVar("owner_id", ComputerManager.NO_OWNER_UUID, false,
            null, RebarSerializers.UUID);

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean preservedState = new AtomicBoolean(false);

    protected Computer() {
        var fileSystemConfig = Configuration.unix().toBuilder()
                .setWorkingDirectory(HOME_PATH)
                .setMaxSize(getFileSystemSize());
        configureFileSystem(fileSystemConfig);
        fileSystem = Jimfs.newFileSystem(fileSystemConfig.build());
        dispatcher = new CommandDispatcher<>();
        scriptRunner = new ScriptRunner(this);
        screen = Screen.create(new BufferedScreenRefresher(this));
        workingDirectory = new AtomicReference<>(fileSystem.getPath(HOME_PATH));
    }

    /**
     * Called on the reboot command/action.
     */
    public abstract void reboot();

    /**
     * Called on shutdown action.
     *
     * @param reason reason of the shutdown
     */
    public abstract void shutdown(@Nullable String reason);

    /**
     * Called on the destroy action.
     */
    public abstract void destroy();

    public final String getName() {
        return name.get();
    }

    public final void setName(String name) {
        Preconditions.checkNotNull(name, "name");
        this.name.set(name);
    }

    /**
     * Returns a unique id of this computer instance.
     * <p>
     * This id is not serialized on computer shutdown and is unique
     * every time new {@link Computer} instance is created.
     *
     * @return unique id of this computer
     */
    public final UUID getUid() {
        return uid.get();
    }

    public final Optional<UUID> getOwner() {
        UUID ownerId = this.ownerId.get();
        return ownerId.equals(ComputerManager.NO_OWNER_UUID)
                ? Optional.empty()
                : Optional.of(ownerId);
    }

    public final FileSystem getFileSystem() {
        return fileSystem;
    }

    public final int executeCommand(@Nullable Player as, String command) throws CommandSyntaxException {
        int got;
        synchronized (dispatcher) {
            got = dispatcher.execute(command, new Executor<>(this, as));
        }
        refreshScreen();
        return got;
    }

    public final @Unmodifiable List<ComputerCommand.Info> getRegisteredCommands() {
        return Collections.unmodifiableList(registeredCommands);
    }

    public final boolean isRunningScript() {
        synchronized (scriptRunner) {
            return runningScript != null;
        }
    }

    public final Optional<ScriptRunner.RunningScript> getRunningScript() {
        synchronized (scriptRunner) {
            return Optional.ofNullable(runningScript);
        }
    }

    public final CompletableFuture<ScriptRunner.Result> runScript(String source) {
        ScriptRunner.RunningScript runningScript;

        synchronized (scriptRunner) {
            Preconditions.checkState(this.runningScript == null, "Only a single script can run at "
                    + "the time");
            runningScript = scriptRunner.execute(source);
            this.runningScript = runningScript;
        }

        refreshScreen();

        return setupRunningScriptFuture(runningScript);
    }

    private CompletableFuture<ScriptRunner.Result> setupRunningScriptFuture(ScriptRunner.RunningScript runningScript) {
        return runningScript.getCompleteFuture().handleAsync((result, e) -> {
            synchronized (scriptRunner) {
                this.runningScript = null;
            }

            refreshScreen();

            Throwable error = e;
            if (error == null && result != null) {
                error = result.error();
            }
            if (error != null) {
                printError("Failed the script execution", error);
                return null;
            }

            print("Finished the script execution: " + (result != null ? result.returned() : null));
            return null;
        });
    }

    public final @Unmodifiable List<ScriptExtension> getRegisteredExtensions() {
        return ScriptExtension.Registry.get().getExtensions().stream()
                .filter(extension -> extension.exportFunctions(this))
                .toList();
    }

    public @Nullable String getBootMessage() {
        return """
                Hello World! OS loaded.
                Use 'help' to display the help screen.
                """;
    }

    public String getHelp() {
        return """
                Welcome to %s!
                Use 'commands' and 'apis' to display list of all available commands and \
                script extensions.
                To get more documentation about a specific command or script extension use \
                'commands <command>' or 'apis <extension>'.
                See %s to get familiar with the Jactl scripting language computers use.
                Use 'Capture' button to see full text output of executed command in case the \
                computer screen is too small.
                """.formatted(Colors.GOLD + "Turtles " + Turtles.version() + Colors.RESET,
                Colors.BLUE + "jactl.io" + Colors.RESET);
    }

    public final Screen getScreen() {
        return screen;
    }

    public final void print(Collection<String> lines) {
        lines.forEach(this::print);
    }

    public final void print(String... lines) {
        for (String line : lines) {
            print(line);
        }
    }

    public final void print(String line) {
        Preconditions.checkNotNull(line, "line");
        String got = processLine(line);
        if (got != null) {
            screen.print(got);
        }
    }

    public final void printError(String reason, Throwable error) {
        Preconditions.checkNotNull(reason, "reason");
        Preconditions.checkNotNull(error, "error");
        if (error.getMessage() != null) {
            reason += ": \n" + error.getMessage();
        }
        print(Colors.RED + reason);
    }

    /**
     * Creates new {@link Writer} implementation that prints to the
     * screen. Lines get normally processed as if printed via {@link #print(String)}.
     *
     * @return new screen writer
     */
    public final Writer createScreenWriter() {
        return new ScreenWriter(this);
    }

    /**
     * Called for every line printed to the screen by computer ({@link #print(String)}).
     * This can be intentionally bypassed by accessing the Screen and printing content directly.
     * <p>
     * If {@code null} is returned, no line will be printed to the computer screen.
     *
     * @param line line to print
     * @return processed line
     */
    @ForOverride
    protected @Nullable String processLine(String line) {
        return line;
    }

    public final Path getWorkingDirectory() {
        return workingDirectory.get();
    }

    /**
     * Updates the current working directory.
     * <p>
     * The {@code updateFunction} should be pure as it may be called multiple times
     * if the update fails due to race conditions.
     *
     * @param updateFunction function to update the current path, gets current path on input
     * @return updated path
     */
    public final Path updateWorkingDirectory(UnaryOperator<Path> updateFunction) {
        Preconditions.checkNotNull(updateFunction, "updateFunction");
        return workingDirectory.updateAndGet(prev -> {
            var updated = updateFunction.apply(prev);
            ensurePath(updated);
            return updated;
        });
    }

    public final @UnmodifiableView Set<EnvVar<?>> getEnvironmentVariables() {
        return Collections.unmodifiableSet(environmentVariables);
    }

    public final Optional<EnvVar<?>> getEnvironmentVariable(String name) {
        return getEnvironmentVariables().stream().filter(v -> v.getName().equals(name)).findAny();
    }


    /**
     * Registers a new static environment variable for this computer.
     * <p>
     * Environment variables can be read by players and modified by players if {@code modifiable}
     * is {@code true}. They can be always modified by code (plugins) {@link EnvVar#set(Object)}.
     * <p>
     * All static variables are automatically serialized when computer is unloaded.
     *
     * @param name name of the environment variable, supports lowercase letters, digits and underscores
     * @param defaultValue default value of the variable, {@code null} values are unsupported
     * @param modifiable whether the variable can be modified by players using the computer or only by plugins
     * @param argumentType argument type representing the value type of this variable. Can be {@code null}
     *                     for unmodifiable variables.
     * @param persistentDataType pdc data type used for serialization
     * @param updateHook optional runnable to execute when this variable is updated
     * @return environment variable
     * @param <T> environment variable type
     */
    protected final <T> EnvVar<T> envVar(String name, T defaultValue, boolean modifiable,
                                         @Nullable ArgumentType<T> argumentType, PersistentDataType<?, T> persistentDataType,
                                         @Nullable Runnable updateHook) {
        return registerEnvVar(new StaticEnvVar<>(name, defaultValue, modifiable, argumentType, persistentDataType, updateHook));
    }

    protected final <T> EnvVar<T> envVar(String name, T defaultValue, boolean modifiable,
                                         @Nullable ArgumentType<T> argumentType, PersistentDataType<?, T> persistentDataType) {
        return envVar(name, defaultValue, modifiable, argumentType, persistentDataType, null);
    }

    /**
     * Registers a new dynamic environment variable for this computer.
     * <p>
     * Dynamic variables are read-only and resolve their value dynamically
     * via the provided supplier. They are not serialized.
     *
     * @param name name of the environment variable, supports lowercase letters, digits and underscores
     * @param supplier supplier providing the value of this variable
     * @return environment variable
     * @param <T> environment variable type
     */
    protected final <T> EnvVar<T> dynamicEnvVar(String name, Supplier<T> supplier) {
        return registerEnvVar(new DynamicEnvVar<>(name, supplier));
    }

    protected final EnvVar<String> envVar(String name, String defaultValue, boolean modifiable) {
        return envVar(name, defaultValue, modifiable, null);
    }

    protected final EnvVar<String> envVar(String name, String defaultValue, boolean modifiable, @Nullable Runnable updateHook) {
        return envVar(name, defaultValue, modifiable, modifiable ? StringArgumentType.word() : null, PersistentDataType.STRING, updateHook);
    }

    protected final EnvVar<Integer> envVar(String name, int defaultValue, boolean modifiable) {
        return envVar(name, defaultValue, modifiable, null);
    }

    protected final EnvVar<Integer> envVar(String name, int defaultValue, boolean modifiable, @Nullable Runnable updateHook) {
        return envVar(name, defaultValue, modifiable, modifiable ? IntegerArgumentType.integer() : null, PersistentDataType.INTEGER, updateHook);
    }

    private <T> EnvVar<T> registerEnvVar(EnvVar<T> var) {
        synchronized (environmentVariables) {
            boolean conflicts = environmentVariables.stream()
                    .anyMatch(existing -> existing.getName().equals(var.getName()));
            Preconditions.checkState(!conflicts, "Environment variable '" + var.getName() + "' is already registered");
            environmentVariables.add(var);
            return var;
        }
    }

    /**
     * @return size of the file system in bytes
     */
    public long getFileSystemSize() {
        return Turtles.config().computerFileSystemSize();
    }

    /**
     * Max length of a script this computer can execute,
     * {@code -1} if disabled.
     *
     * @return max length of a script this computer can execute
     */
    public long getMaxScriptLength() {
        return Turtles.config().maxScriptLength();
    }

    /**
     * Max script iterations allowed in script environment of this computer without yielding,
     * {@code -1} if disabled.
     *
     * @return max script iterations allowed in script environment of this computer without yielding
     */
    public long getMaxScriptIterations() {
        return Turtles.config().maxScriptIterations();
    }

    /**
     * Max execution time allowed in script environment of this computer without yielding,
     * {@code -1} if disabled.
     *
     * @return max execution time allowed in script environment of this computer without yielding
     */
    public int getMaxScriptExecutionTime() {
        return Turtles.config().maxScriptExecTime();
    }

    /**
     * Max memory usage of a running script,
     * {@code -1} if disabled.
     *
     * @return max memory usage of a running script
     */
    public long getMaxScriptMemoryUsage() {
        return Turtles.config().maxScriptMemoryUsage();
    }

    /**
     * Used for additional configuration of the JIMFS used by the computer.
     *
     * @param config JIMFS config
     */
    @ForOverride
    protected void configureFileSystem(Configuration.Builder config) {
    }

    /**
     * Called during the first environment initialization for this computer.
     * Is not called again for environment deserialization.
     */
    @ForOverride
    @MustBeInvokedByOverriders
    protected void initEnvironment() {
    }

    /**
     * Called during the first FS initialization for this computer.
     * Is not called again for FS deserialization.
     *
     * @param fileSystem file system
     */
    @ForOverride
    @MustBeInvokedByOverriders
    protected void initFileSystem(FileSystem fileSystem) throws IOException {
        new ComputerFilesInitEvent(this).callEvent();
    }

    /**
     * Called when initializing the commands available by the computer OS.
     *
     * @param commands modifiable commands collection
     */
    @ForOverride
    @MustBeInvokedByOverriders
    protected void initCommands(/* modifiable */ List<ComputerCommand<Computer>> commands) {
        commands.addAll(List.of(
                Ls.get(), Pwd.get(), Clear.get(), Cd.get(), Touch.get(), Type.get(),
                Mkdir.get(), Rm.get(), Cat.get(), Edit.get(), Mv.get(), Cp.get(), Env.get(),
                Reboot.get(), Destroy.get(), Run.get(), Less.get(), Help.get(), Commands.get(),
                Apis.get(), Status.get(), Shutdown.get()
        ));
    }

    private final NamespacedKey PRESERVED_STATE_KEY = TurtlesKeys.key("preserved_state");

    /**
     * Called only if the computer was previously serialized as PDC.
     * <p>
     * File system is serialized by default implementation.
     *
     * @param pdc PDC
     */
    @ForOverride
    @MustBeInvokedByOverriders
    protected void deserialize(PersistentDataContainer pdc) throws IOException {
        preservedState.set(pdc.getOrDefault(PRESERVED_STATE_KEY, PersistentDataType.BOOLEAN, false));
        loadEnvironment(pdc);
        loadFileSystem(fileSystem, pdc);
        if (preservedState()) {
            loadScreen(screen, pdc);
            loadCheckpoint(scriptRunner, pdc);
        }
    }

    /**
     * Called after the computer initialization.
     * <p>
     * This is called both after already existing computer is booted again
     * and after completely new computer is created.
     */
    @ForOverride
    @MustBeInvokedByOverriders
    protected void postInit() throws IOException {
        if (!preservedState()) {
            String bootMessage = getBootMessage();
            if (bootMessage != null) {
                print(bootMessage);
            }
        }
    }

    /**
     * Serializes the computer data into PDC.
     * <p>
     * File system is serialized by default implementation.
     *
     * @param pdc PDC
     * @param preserveState whether the running state such as screen or executing
     *                      script state should be preserved
     */
    @MustBeInvokedByOverriders
    public void serialize(PersistentDataContainer pdc, boolean preserveState) throws IOException {
        pdc.set(PRESERVED_STATE_KEY, PersistentDataType.BOOLEAN, preserveState);
        saveEnvironment(pdc);
        saveFileSystem(fileSystem, pdc);

        if (preserveState) {
            saveScreen(screen, pdc);
            saveCheckpoint(scriptRunner, pdc);
        }
    }

    private static final String COMMAND_INPUT = "command_input";

    /**
     * The label for the command input.
     *
     * @param player player the dialog window is for
     * @return label
     */
    protected Component getInputLabel(Player player) {
        return Component.text()
                .append(Component.text(player.getName() + "@" + getName(), NamedTextColor.GREEN))
                .append(Component.text(":"))
                .append(Component.text(getWorkingDirectory().toAbsolutePath().toString(), NamedTextColor.GREEN))
                .append(Component.text("$"))
                .asComponent();
    }

    /**
     * Adds additional inputs to the computer dialog screen.
     * <p>
     * Does not contain the computer screen as it is not considered an input (even when
     * technically it is a multi-line dialog text input).
     *
     * @param player player the dialog is being built for
     * @param inputs modifiable inputs list
     */
    @ForOverride
    @MustBeInvokedByOverriders
    protected void addInputs(Player player, /* modifiable */ List<DialogInput> inputs) {
        if (isRunningScript()) {
            return;
        }

        Component label = Preconditions.checkNotNull(getInputLabel(player), "label");
        inputs.add(DialogInput.text(COMMAND_INPUT, label)
                .maxLength(64)
                .width(380)
                .build());
    }

    /**
     * Adds additional action buttons to the computer dialog screen.
     *
     * @param player player the dialog is being built for
     * @param actionButtons modifiable action buttons list
     */
    @ForOverride
    @MustBeInvokedByOverriders
    protected void addActionButtons(Player player, /* modifiable */ List<ActionButton> actionButtons) {
        if (!isRunningScript()) {
            actionButtons.addAll(List.of(
                    DialogUtils.actionButton("Execute", (response, _) -> {
                        String command = Preconditions.checkNotNull(response.getText(COMMAND_INPUT), COMMAND_INPUT)
                                .trim();
                        if (command.isEmpty()) {
                            return;
                        }
                        try {
                            executeCommand(player, command);
                        } catch (CommandSyntaxException e) {
                            print(Colors.RED + e.getRawMessage().getString());
                            print("Pos: " + e.getCursor() + ": " + e.getContext());
                        }
                    }),
                    DialogUtils.actionButton("Capture", (response, _) -> {
                        String command = Preconditions.checkNotNull(response.getText(COMMAND_INPUT), COMMAND_INPUT)
                                .trim();
                        if (command.isEmpty()) {
                            return;
                        }
                        screen.startCapture();
                        try {
                            executeCommand(player, command);
                        } catch (CommandSyntaxException e) {
                            print(Colors.RED + e.getRawMessage().getString());
                            print("Pos: " + e.getCursor() + ": " + e.getContext());
                        }
                        String captured = Colors.stripColors(String.join("\n", screen.endCapture()));

                        DialogInput viewInput = DialogUtils.viewInput("captured", captured);

                        List<ActionButton> buttons = List.of(
                                DialogUtils.actionButton("Close", (_, _) -> {
                                    if (!addToActiveViewers(player, true)) {
                                        player.closeDialog();
                                    }
                                })
                        );

                        Dialog dialog = Dialog.create(builder -> builder.empty()
                                .base(DialogUtils.dialogBase(
                                        Component.text("Viewing captured command output"),
                                        null, List.of(viewInput)))
                                .type(DialogType.multiAction(buttons, null, 5))
                        );

                        if (removeFromActiveViewers(player, false)) {
                            player.showDialog(dialog);
                        }
                    })
            ));
        } else {
            actionButtons.add(DialogUtils.actionButton("Terminate", (_, _) ->
                    getRunningScript().ifPresent(script -> script.terminate("Terminated"))));
        }

        actionButtons.addAll(List.of(
                DialogUtils.actionButton("Reboot", (_, _) -> reboot()),
                DialogUtils.actionButton("Shutdown", (_, _) ->
                        shutdown("Turned off"))
        ));
    }

    /**
     * Opens the computer for the player.
     *
     * @param player player to open the computer for
     */
    public final void open(Player player) {
        if (addToActiveViewers(player, true)) {
            player.playSound(Sound.sound(Key.key("block.copper_chest.open"),
                    Sound.Source.PLAYER, 1, 1.5f), Sound.Emitter.self());
        }
    }

    /**
     * Refreshes the computer screen.
     */
    public final void refreshScreen() {
        synchronized (activeViewers) {
            if (isClosed()) {
                return;
            }
            activeViewers.forEach(this::refreshScreen);
        }
    }

    /**
     * Adds player to active viewers of the computer screen.
     * <p>
     * The player will then receive computer screen updates.
     *
     * @param player player to add to active viewers
     * @param openScreen whether the computer screen should be opened
     * @return if player was added to active viewers
     */
    public final boolean addToActiveViewers(Player player, boolean openScreen) {
        synchronized (activeViewers) {
            if (isClosed()) {
                return false;
            }
            boolean added = activeViewers.add(player);
            if (added && openScreen) {
                refreshScreen(player);
            }
            return added;
        }
    }

    /**
     * Removes player from active viewers of the computer screen.
     * <p>
     * The player will no longer receive computer screen updates.
     *
     * @param player player to remove from active viewers
     * @param closeScreen whether the computer screen should be closed
     * @return if player was removed from active viewers
     */
    public final boolean removeFromActiveViewers(Player player, boolean closeScreen) {
        synchronized (activeViewers) {
            boolean removed = activeViewers.remove(player);
            if (removed && closeScreen) {
                player.closeDialog();
            }
            return removed;
        }
    }

    private static final int
            DEFAULT_SCREEN_DIALOG_WIDTH = 380,
            DEFAULT_SCREEN_DIALOG_HEIGHT = 160;

    public void refreshScreen(Player player) {
        synchronized (activeViewers) {
            if (isClosed() || !activeViewers.contains(player)) {
                return;
            }

            List<DialogInput> inputs = new ArrayList<>();
            inputs.add(DialogInput.text("screen_output", Component.empty())
                    .maxLength(Integer.MAX_VALUE)
                    .width(DEFAULT_SCREEN_DIALOG_WIDTH)
                    .multiline(TextDialogInput.MultilineOptions
                            .create(Screen.DEFAULT_SCREEN_LINES, DEFAULT_SCREEN_DIALOG_HEIGHT))
                    .initial(String.join("\n", screen.render()))
                    .build());
            addInputs(player, inputs);

            List<ActionButton> actionButtons = new ArrayList<>();
            addActionButtons(player, actionButtons);

            Dialog dialog = Dialog.create(builder -> builder.empty()
                    .base(DialogUtils.dialogBase(Component.text(getName(), Style.style(TextDecoration.BOLD)), null, inputs))
                    .type(DialogUtils.multiAction(actionButtons, (_, _) ->
                            removeFromActiveViewers(player, true), 4))
            );

            MainThread.run(() -> player.showDialog(dialog)).join();
        }
    }

    public final boolean isClosed() {
        return closed.get();
    }

    protected final boolean preservedState() {
        return preservedState.get();
    }

    /**
     * @param preserveState whether the running state such as screen or executing
     *                      script state should be preserved
     * @apiNote must be called on the main thread
     */
    public final void close(boolean preserveState) throws Exception {
        serializeAndClose(null, preserveState);
    }

    /**
     * @param pdc container where to serialize the computer data,
     *            {@code null} if the serialization should be skipped
     * @param preserveState whether the running state such as screen or executing
     *                      script state should be preserved
     * @apiNote must be called on the main thread
     */
    public final void serializeAndClose(@Nullable PersistentDataContainer pdc, boolean preserveState) throws Exception {
        MainThread.ensure();
        if (closed.compareAndSet(false, true)) {
            if (pdc != null) {
                serialize(pdc, preserveState);
            }
            onClose();
        }
    }

    @ForOverride
    @MustBeInvokedByOverriders
    protected void onClose() throws Exception {
        synchronized (activeViewers) {
            List<Player> toRemove = List.copyOf(activeViewers);
            toRemove.forEach(p -> removeFromActiveViewers(p, true));
        }
        fileSystem.close();
        synchronized (scriptRunner) {
            ScriptRunner.RunningScript runningScript = this.runningScript;
            if (runningScript != null) {
                runningScript.terminate("Computer shutdown");
            }
        }
        ComputerManager.get().close(this);
    }

    private static final NamespacedKey ENV_KEY = TurtlesKeys.key("environment");

    void loadEnvironment(PersistentDataContainer pdc) {
        PersistentDataContainer envVarMap = pdc.get(ENV_KEY, PersistentDataType.TAG_CONTAINER);
        if (envVarMap == null) {
            return;
        }
        for (EnvVar<?> environmentVariable : environmentVariables) {
            environmentVariable.load(envVarMap);
        }
    }

    void saveEnvironment(PersistentDataContainer pdc) {
        PersistentDataContainer envVarMap = pdc.getAdapterContext().newPersistentDataContainer();
        for (EnvVar<?> environmentVariable : environmentVariables) {
            environmentVariable.write(envVarMap);
        }
        pdc.set(ENV_KEY, PersistentDataType.TAG_CONTAINER, envVarMap);
    }

    private static final NamespacedKey FS_KEY = TurtlesKeys.key("file_system");

    void loadFileSystem(FileSystem fileSystem, PersistentDataContainer pdc) throws IOException {
        unzipFileSystem(pdc.get(FS_KEY, PersistentDataType.BYTE_ARRAY), fileSystem);
    }

    void saveFileSystem(FileSystem fileSystem, PersistentDataContainer pdc) throws IOException {
        pdc.set(FS_KEY, PersistentDataType.BYTE_ARRAY, zipFileSystem(fileSystem));
    }

    private static final NamespacedKey SCREEN_KEY = TurtlesKeys.key("screen");

    void loadScreen(Screen screen, PersistentDataContainer pdc) {
        if (pdc.has(SCREEN_KEY)) {
            screen.print(pdc.get(SCREEN_KEY, PersistentDataType.STRING));
        }
    }

    void saveScreen(Screen screen, PersistentDataContainer pdc) {
        pdc.set(SCREEN_KEY, PersistentDataType.STRING, String.join("\n", screen.render()));
    }

    private static final NamespacedKey SCRIPT_SOURCE_KEY = TurtlesKeys.key("script_source");
    private static final NamespacedKey CHECKPOINT_KEY = TurtlesKeys.key("checkpoint");

    void loadCheckpoint(ScriptRunner scriptRunner, PersistentDataContainer pdc) {
        if (!this.scriptRunner.equals(scriptRunner)) {
            return;
        }
        synchronized (this.scriptRunner) {
            if (!pdc.has(CHECKPOINT_KEY)) {
                return;
            }
            String source = pdc.get(SCRIPT_SOURCE_KEY, PersistentDataType.STRING);
            byte[] checkpoint = pdc.get(CHECKPOINT_KEY, PersistentDataType.BYTE_ARRAY);
            try {
                ScriptRunner.RunningScript runningScript = this.scriptRunner.recover(source, checkpoint);
                this.runningScript = runningScript;
                setupRunningScriptFuture(runningScript);
            } catch (Exception e) {
                printError("Failed to recover running script", e);
            }
        }
    }

    void saveCheckpoint(ScriptRunner scriptRunner, PersistentDataContainer pdc) {
        if (!this.scriptRunner.equals(scriptRunner)) {
            return;
        }
        synchronized (this.scriptRunner) {
            ScriptRunner.RunningScript runningScript = this.runningScript;
            if (runningScript == null) {
                return;
            }
            runningScript.getCheckpoint().ifPresent(checkpoint -> {
                pdc.set(SCRIPT_SOURCE_KEY, PersistentDataType.STRING, runningScript.getSource());
                pdc.set(CHECKPOINT_KEY, PersistentDataType.BYTE_ARRAY, checkpoint);
            });
        }
    }

    void initDispatcher() {
        List<ComputerCommand<Computer>> commands = new ArrayList<>();
        initCommands(commands);
        new ComputerCommandsInitEvent(this, commands).callEvent();

        for (var command : commands) {
            var builder = command.build();
            String label = builder.getLiteral();
            String description = command.getClass().isAnnotationPresent(Docs.class)
                    ? command.getClass().getAnnotation(Docs.class).value()
                    : null;
            registeredCommands.add(new ComputerCommand.Info(label, description));
            synchronized (dispatcher) {
                dispatcher.register(builder);
            }
        }
    }

    private void ensurePath(Path path) {
        Preconditions.checkNotNull(path, "path");
        Preconditions.checkState(path.getFileSystem().equals(fileSystem), "Path must be from the "
                + "computer file system");
    }

    private static byte[] zipFileSystem(FileSystem fs) throws IOException {
        Path root = fs.getPath("/");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos); Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> !path.equals(root)).forEach(path -> {
                try {
                    String name = root.relativize(path).toString();
                    if (Files.isDirectory(path)) {
                        zos.putNextEntry(new ZipEntry(name + "/"));
                        zos.closeEntry();
                    } else {
                        zos.putNextEntry(new ZipEntry(name));
                        Files.copy(path, zos);
                        zos.closeEntry();
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        return baos.toByteArray();
    }

    private static void unzipFileSystem(byte @Nullable [] data, FileSystem fs) throws IOException {
        if (data == null || data.length == 0) {
            return;
        }
        Path root = fs.getPath("/");
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = root.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

}
