package dev.pesek.turtles.computer.script;

import com.google.common.base.Preconditions;
import dev.pesek.turtles.Turtles;
import io.jactl.JactlContext;
import io.jactl.JactlEnv;
import io.jactl.JactlScript;
import io.jactl.runtime.Continuation;
import io.jactl.runtime.JactlScriptObject;
import io.jactl.runtime.Restorer;
import io.jactl.runtime.RuntimeState;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Jactl environment implementation for running computer scripts.
 * <p>
 * Compared to default JactlEnv, this implementation allows for
 * capturing {@link ScriptRunner.RunningScript} before scheduling events
 * and running checks on the running script instance on context switching.
 * <p>
 * Any calls to schedule events on this environment from outside will fail.
 * To run a script, use {@link #runScript(JactlContext, JactlScript, Map, Reader, Writer, ComputerInvocationContext)}.
 */
// TODO collect metrics which running scripts take the most execution time
//  on the shared event loop thread pool
// TODO consider forking Jactl, yielding, memory checks and script recovery are a bit clunky now
//  and would benefit from a custom implementation directly within Jactl
public final class ComputerEnv implements JactlEnv {

    private static final ComputerEnv INSTANCE = new ComputerEnv();

    /**
     * @return the computer environment instance
     */
    public static ComputerEnv get() {
        return INSTANCE;
    }

    // captured RunningScript instance
    private static final ThreadLocal<ScriptRunner.RunningScript> CURRENT_INVOCATION = new ThreadLocal<>();

    /**
     * Runs a script in this environment.
     *
     * @param context jactl context used for the execution
     * @param script compiled jactl script to run
     * @param globals map of globals
     * @param input std input
     * @param output std output
     * @param invocationContext invocation context
     * @return running script instance
     */
    public static ScriptRunner.RunningScript runScript(JactlContext context, JactlScript script,
                                                       Map<String, Object> globals,
                                                       @Nullable Reader input, @Nullable Writer output,
                                                       ComputerInvocationContext invocationContext) {
        Preconditions.checkNotNull(context, "context");
        Preconditions.checkNotNull(script, "script");
        Preconditions.checkNotNull(globals, "globals");
        Preconditions.checkNotNull(invocationContext, "invocationContext");

        ScriptRunner.RunningScript runningScript = new ScriptRunner.RunningScript(invocationContext, new CompletableFuture<>());

        try {
            CURRENT_INVOCATION.set(runningScript);
            context.scheduleEvent(null, () -> script.run(globals, input, output,
                    runningScript.getInvocationContext(),
                    result -> runningScript.getCompleteFuture().complete(ScriptRunner.Result.from(result))));
        } finally {
            ComputerEnv.CURRENT_INVOCATION.remove();
        }

        return runningScript;
    }

    public static ScriptRunner.RunningScript recoverScript(JactlContext context, byte[] checkpoint,
                                                           @Nullable Reader input, @Nullable Writer output,
                                                           ComputerInvocationContext invocationContext) {
        Preconditions.checkNotNull(context, "context");
        Preconditions.checkNotNull(checkpoint, "checkpoint");
        Preconditions.checkNotNull(invocationContext, "invocationContext");

        ScriptRunner.RunningScript runningScript = new ScriptRunner.RunningScript(invocationContext, new CompletableFuture<>());

        try {
            CURRENT_INVOCATION.set(runningScript);

            Continuation cont = (Continuation) Restorer.restore(context, checkpoint);
            // If two args then we have commit closure and recovery closure so return recovery closure on recover
            Object result = cont.localObjects.length == 1 ? cont.localObjects[0] : cont.localObjects[1];
            RuntimeState state = RuntimeState.getState();
            RuntimeState.setState(state.getContext(), state.getGlobals(), input, output, invocationContext);

            context.scheduleEvent(null, () -> resumeContinuation(context,
                    r -> runningScript.getCompleteFuture().complete(ScriptRunner.Result.from(r)),
                    result, cont, cont.scriptInstance, state));

        } finally {
            ComputerEnv.CURRENT_INVOCATION.remove();
        }

        return runningScript;
    }

    private static void resumeContinuation(JactlContext context, Consumer<Object> completion, Object asyncResult,
                                           Continuation cont, JactlScriptObject instance, RuntimeState state) {
        RuntimeState.setState(state);
        try {
            Object result = cont.continueExecution(asyncResult);
            cleanUp(context, instance);
            completion.accept(result);
        } catch (Continuation c) {
            context.asyncWork(completion, c, instance);
        } catch (Throwable t) {
            cleanUp(context, instance);
            completion.accept(t);
        }
    }

    private static void cleanUp(JactlContext context, JactlScriptObject instance) {
        RuntimeState.resetState();
        if (instance != null && instance._$j$isCheckpointed()) {
            instance._$j$incrementCheckpointId();
            context.deleteCheckpoint(instance._$j$getInstanceId(), instance._$j$checkpointId());
        }
    }

    private final ExecutorService eventLoop = Executors
            .newFixedThreadPool(Turtles.config().scriptEnvironmentEventLoopThreadCount());
    private final ExecutorService blockingExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService timerService = Executors.newSingleThreadScheduledExecutor();

    private ComputerEnv() {
    }

    @Override
    public void scheduleEvent(@Nullable Object threadContext, Runnable event) {
        consumeInvocation(currentInvocation ->
                eventLoop.submit(exposeInvocation(event, currentInvocation)));
    }

    @Override
    public void scheduleEvent(@Nullable Object threadContext, Runnable event, long timeMs) {
        if (timeMs <= 0) {
            scheduleEvent(threadContext, event);
        } else {
            consumeInvocation(currentInvocation -> timerService.schedule(
                    exposeInvocation(() -> scheduleEvent(threadContext, event), currentInvocation),
                    timeMs, TimeUnit.MILLISECONDS));
        }
    }

    @Override
    public void scheduleEvent(Runnable event, long timeMs) {
        scheduleEvent(null, event, timeMs);
    }

    @Override
    public void scheduleBlocking(Runnable blocking) {
        consumeInvocation(currentInvocation ->
                // Computer invocation context stores the task future
                // so it can be interrupted in case the invocation is terminated.
                currentInvocation.getInvocationContext().scheduleBlockingTask(() ->
                        blockingExecutor.submit(exposeInvocation(blocking, currentInvocation))));
    }

    @Override
    public @Nullable Object getThreadContext() {
        return null;
    }

    @Override
    public void saveCheckpoint(UUID id, int checkpointId, byte[] checkpoint, String source,
                               int offset, Object result, Consumer<Object> resumer) {
        consumeInvocation(currentInvocation -> {
            currentInvocation.getInvocationContext().captureCheckpoint(checkpointId, checkpoint);
            exposeInvocation(() -> resumer.accept(result), currentInvocation).run();
        });
    }

    public void shutdown() {
        eventLoop.shutdown();
        blockingExecutor.shutdown();
        timerService.shutdown();
    }

    /**
     * Executed each time before new event is scheduled.
     * <p>
     * Checks if the script should be terminated (either on request or because of high memory usage)
     * and if yes, completes its future with the result.
     *
     * @param currentInvocation current running script
     * @see ScriptRunner.RunningScript#getCompleteFuture()
     * @see ScriptRunner.RunningScript#terminate(Throwable)
     * @see dev.pesek.turtles.computer.script.ScriptRunner.Result
     */
    private void onInvocationContextSwitch(ScriptRunner.RunningScript currentInvocation) {
        ComputerInvocationContext invocationContext = currentInvocation.getInvocationContext();

        if (MemoryCheck.shouldCheckForMemory() && invocationContext.getComputer().getMaxScriptMemoryUsage() >= 0) {
            invocationContext.recordMemoryUsage();
            long usedMemory = invocationContext.getLastRecordedMemoryUsage();
            if (usedMemory > invocationContext.getComputer().getMaxScriptMemoryUsage()) {
                currentInvocation.terminate("Computer ran out of memory");
            }
        }

        if (MemoryCheck.shouldKillScripts()) {
            currentInvocation.terminate("Server ran out of memory");
        }

        invocationContext.shouldTerminate().ifPresent(t ->
                currentInvocation.getCompleteFuture().complete(ScriptRunner.Result.from(t)));
    }

    /**
     * Accepts and removes the current {@link ScriptRunner.RunningScript} instance.
     *
     * @param consumer consumer of the running script
     * @see #exposeInvocation(Runnable, ScriptRunner.RunningScript)
     */
    private void consumeInvocation(Consumer<ScriptRunner.RunningScript> consumer) {
        try {
            ScriptRunner.RunningScript currentInvocation = CURRENT_INVOCATION.get();
            Preconditions.checkNotNull(currentInvocation, "currentInvocation");

            // checks if invocation should terminate and if yes completes its future
            onInvocationContextSwitch(currentInvocation);
            if (currentInvocation.isDone()) {
                return; // do not run further if done
            }

            consumer.accept(currentInvocation);
        } finally {
            CURRENT_INVOCATION.remove();
        }
    }

    /**
     * Exposes the current {@link ScriptRunner.RunningScript} instance so it can be later consumed.
     *
     * @param task task in which the instance should be exposed
     * @param invocationContext running script instance to expose
     * @return wrapped task in which the running script instance can be consumed
     * @see #consumeInvocation(Consumer)
     */
    private Runnable exposeInvocation(Runnable task, ScriptRunner.RunningScript invocationContext) {
        Preconditions.checkNotNull(invocationContext, "invocationContext");
        return () -> {
            try {
                CURRENT_INVOCATION.set(invocationContext);
                task.run();
            } finally {
                CURRENT_INVOCATION.remove();
            }
        };
    }

}
