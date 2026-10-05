package dev.pesek.turtles.computer.script;

import com.google.common.base.Preconditions;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.util.FreezingRegistry;
import dev.pesek.turtles.util.MainThread;
import io.jactl.JactlContext;
import io.jactl.runtime.Continuation;
import io.jactl.runtime.RuntimeError;
import io.jactl.runtime.RuntimeState;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

/**
 * Extension for the Jactl computer scripting environment.
 * <p>
 * To easily add new functions, types and methods use {@link Export} annotations.
 * To add documentation, annotate the script extension class and exported API
 * with {@link dev.pesek.turtles.util.Docs}.
 */
public interface ScriptExtension {

    /**
     * Registers the script extension.
     * <p>
     * The registration must be done before the server is loaded.
     * It is not possible to register script extensions after
     * server loading has finished.
     *
     * @param scriptExtension script extension to register
     */
    static void register(ScriptExtension scriptExtension) {
        Registry.get().register(scriptExtension);
    }

    /**
     * @return all types exported by this script extension
     */
    static @Unmodifiable List<Class<?>> exportTypes(ScriptExtension scriptExtension) {
        List<Class<?>> types = new ArrayList<>();
        if (scriptExtension.exportSelf()) {
            types.add(scriptExtension.getClass());
        }
        types.addAll(scriptExtension.extraTypes());
        return Collections.unmodifiableList(types);
    }

    /**
     * @return current computer invocation context if available
     */
    static ComputerInvocationContext currentContext() {
        return (ComputerInvocationContext) Preconditions.checkNotNull(RuntimeState.getState().getInvocationContext(),
                "Missing computer invocation context");
    }

    /**
     * Helper method to run an action on the main thread.
     *
     * @param source source
     * @param offset offset
     * @param callable callable to run on the main thread
     * @param msDelay delay in ms after the action on the main thread, before resuming the code execution
     * @return none, always throws
     * @param <V> returned value
     * @throws Continuation always
     */
    static <V> Continuation onMainThread(String source, int offset, ComputerCallable<V> callable, long msDelay) throws Continuation {
        Preconditions.checkNotNull(callable, "callable");
        throw Continuation.suspendNonBlocking(source, offset, null, (context, _, resumer) -> {
            ComputerEnv.get().consumeInvocation(currentInvocation -> MainThread.run(() -> {
                try {
                    // TODO do some util in computer env that allows to schedule task on executor
                    //  while keeping the invocation instance available
                    V result = callable.call(currentInvocation.getInvocationContext());
                    ComputerEnv.get().exposeInvocation(() ->
                            context.scheduleEvent(() -> resumer.accept(result), msDelay), currentInvocation).run();
                } catch (Exception e) {
                    resumer.accept(new RuntimeError("Error running on main thread", source, offset, e));
                }
            }));
        });
    }

    static <V> Continuation onMainThread(String source, int offset, ComputerCallable<V> callable) throws Continuation {
        return onMainThread(source, offset, callable, 0);
    }

    static Continuation onMainThread(String source, int offset, ComputerRunnable runnable, long msDelay) throws Continuation {
        Preconditions.checkNotNull(runnable, "runnable");
        return onMainThread(source, offset, invocationContext -> {
            runnable.run(invocationContext);
            return null;
        }, msDelay);
    }

    static Continuation onMainThread(String source, int offset, ComputerRunnable runnable) throws Continuation {
        return onMainThread(source, offset, runnable, 0);
    }

    /**
     * Helper method to suspend the code execution after a call.
     *
     * @param source source
     * @param offset offset
     * @param callable callable to run
     * @param msDelay delay in ms after the action, before resuming the code execution
     * @return none, always throws
     * @param <V> returned value
     * @throws Continuation always
     */
    static <V> Continuation delayAfter(String source, int offset, ComputerCallable<V> callable, long msDelay) throws Continuation {
        Preconditions.checkNotNull(callable, "callable");
        ComputerInvocationContext invocationContext = currentContext();
        throw Continuation.suspendNonBlocking(source, offset, null,
                ((context, _, resumer) -> {
                    try {
                        V result = callable.call(invocationContext);
                        context.scheduleEvent(() -> resumer.accept(result), msDelay);
                    } catch (Exception e) {
                        resumer.accept(new RuntimeError("Error running on main thread", source, offset, e));
                    }
                }));
    }

    static Continuation delayAfter(String source, int offset, ComputerRunnable runnable, long msDelay) throws Continuation {
        Preconditions.checkNotNull(runnable, "runnable");
        return delayAfter(source, offset, invocationContext -> {
            runnable.run(invocationContext);
            return null;
        }, msDelay);
    }

    /**
     * @return key of the script extension
     */
    NamespacedKey key();

    /**
     * @return whether the script extension itself should be exported
     */
    default boolean exportSelf() {
        return true;
    }

    /**
     * Provides list of additional types to be exported.
     *
     * @return additional types to export
     */
    default List<Class<?>> extraTypes() {
        return List.of();
    }

    /**
     * Whether functions of this script extensions should be exported
     * for given computer before script execution.
     * <p>
     * Types and their methods are exported to all computers.
     *
     * @param computer computer
     * @return whether to export functions for this computer
     */
    boolean exportFunctions(Computer computer);

    /**
     * Can be used to further configure the Jactl context
     * before script execution.
     *
     * @param computer computer running the script
     * @param context context to configure
     */
    default void configureContext(Computer computer, JactlContext context) {
    }

    /**
     * Defines additional predefined global variables to the script
     * ran by given computer as a {@code name <=> value} map.
     *
     * @param computer computer running the script
     * @return additional predefined global variables
     */
    default Map<String, Object> globals(Computer computer) {
        return Map.of();
    }

    final class Registry extends FreezingRegistry<NamespacedKey, ScriptExtension> {

        private static final Registry INSTANCE = new Registry();

        public static Registry get() {
            return INSTANCE;
        }

        private Registry() {
        }

        @Override
        protected NamespacedKey getKey(ScriptExtension value) {
            return value.key();
        }

    }

}
