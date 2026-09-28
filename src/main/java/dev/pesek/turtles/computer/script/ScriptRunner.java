package dev.pesek.turtles.computer.script;

import com.google.common.base.Preconditions;
import dev.pesek.turtles.computer.Computer;
import io.jactl.CompileError;
import io.jactl.Jactl;
import io.jactl.JactlContext;
import io.jactl.JactlScript;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Class responsible for initializing the environment in which
 * the user scripts can be run.
 */
public final class ScriptRunner {

    private final Computer computer;

    public ScriptRunner(Computer computer) {
        this.computer = Preconditions.checkNotNull(computer, "computer");
    }

    /**
     * Represents the result of a completed (possibly terminated) script.
     *
     * @param returned returned value of the script, can be {@code null}
     * @param error error that happened during the execution, if not {@code null},
     *              it means the script did not finish properly
     */
    public record Result(@Nullable Object returned, @Nullable Throwable error) {
        public static Result from(@Nullable Object any) {
            return switch (any) {
                case Throwable t -> new Result(null, t);
                case null -> new Result(null, null);
                default -> new Result(any, null);
            };
        }
    }

    /**
     * Represents a running script.
     * <p>
     * Exposes the future of when script finishes execution (or is terminated).
     */
    public static final class RunningScript {

        private final ComputerInvocationContext invocationContext;
        private final CompletableFuture<Result> completeFuture;

        RunningScript(ComputerInvocationContext invocationContext, CompletableFuture<Result> future) {
            this.invocationContext = Preconditions.checkNotNull(invocationContext, "invocationContext");
            this.completeFuture = Preconditions.checkNotNull(future, "future");
        }

        /**
         * @return the invocation context
         */
        ComputerInvocationContext getInvocationContext() {
            return invocationContext;
        }

        /**
         * @return future of when script completes execution
         */
        public CompletableFuture<Result> getCompleteFuture() {
            return completeFuture;
        }

        /**
         * @return last captured program checkpoint
         */
        public Optional<byte[]> getCheckpoint() {
            return invocationContext.getCheckpoint();
        }

        /**
         * @return source code of the running script
         */
        public String getSource() {
            return invocationContext.getSource();
        }

        /**
         * @return whether the script finished running
         */
        public boolean isDone() {
            return completeFuture.isDone();
        }

        public void terminate() {
            terminate((Throwable) null);
        }

        public void terminate(String reason) {
            Preconditions.checkNotNull(reason, "reason");
            terminate(new ScriptTerminatedException(reason));
        }

        /**
         * Terminates the running script.
         * <p>
         * The script will be terminated the next time
         * async task is scheduled or completed, or if there is a
         * running blocking task which thread can be interrupted.
         *
         * @param t reason of the termination
         */
        public void terminate(@Nullable Throwable t) {
            invocationContext.terminate(t);
        }

    }

    /**
     * Compiles and executes given source in a new context.
     *
     * @param source source to compile and execute
     * @return running script instance
     * @throws CompileError if compilation fails
     */
    public RunningScript execute(String source) throws CompileError {
        Preconditions.checkNotNull(source, "source");

        validSource(source);

        JactlContext context = createContext(ComputerEnv.get());

        Map<String, Object> globals = new HashMap<>();
        JactlExport.exportGlobals(computer, globals);

        JactlScript script = Jactl.compileScript(source, globals, context);

        return ComputerEnv.runScript(context, script, globals, null, computer.createScreenWriter(),
                new ComputerInvocationContext(computer, source));
    }

    public RunningScript recover(String source, byte[] checkpoint) {
        Preconditions.checkNotNull(checkpoint, "checkpoint");

        validSource(source);

        JactlContext context = createContext(ComputerEnv.get());

        Map<String, Object> globals = new HashMap<>();
        JactlExport.exportGlobals(computer, globals);

        Jactl.compileScript(source, globals, context);

        return ComputerEnv.recoverScript(context, checkpoint,
                null, computer.createScreenWriter(), new ComputerInvocationContext(computer, source));
    }

    private JactlContext createContext(ComputerEnv env) {
        JactlContext context = JactlContext.create()
                .environment(env)
                .maxLoopIterations(computer.getMaxScriptIterations())
                .maxExecutionTime(computer.getMaxScriptExecutionTime())
                .hasOwnFunctions(true)
                .disableEval(true) // for safety reasons
                .build();
        ScriptExtension.Registry.get().getExtensions().forEach(scriptExtension ->
                scriptExtension.configureContext(computer, context));
        JactlExport.exportFunctions(computer, context);
        return context;
    }

    private void validSource(String source) {
        if (source.length() > computer.getMaxScriptLength() && computer.getMaxScriptLength() >= 0) {
            throw new IllegalArgumentException("The script exceeded maximum allowed length "
                    + source.length() + "/" + computer.getMaxScriptLength());
        }
    }

}
