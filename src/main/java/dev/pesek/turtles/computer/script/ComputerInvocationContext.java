package dev.pesek.turtles.computer.script;

import com.google.common.base.Preconditions;
import dev.pesek.turtles.computer.Computer;
import io.jactl.runtime.Continuation;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * Contex of a computer script invocation.
 * <p>
 * Manages the state of the execution; allows for script termination.
 */
public final class ComputerInvocationContext {

    private final Computer computer;
    private final String source;

    private volatile @Nullable Throwable terminationReason;

    private volatile Continuation continuation;
    private volatile long lastRecordedMemoryUsage = 0;

    private final Checkpoint checkpoint = new Checkpoint();

    /**
     * Last task executed with {@link ComputerEnv#scheduleBlocking(Runnable)}.
     * If this invocation is canceled while this task is running (is blocked)
     * it is interrupted.
     */
    private volatile @Nullable Future<?> blockingTask;

    private final Object lock = new Object();

    ComputerInvocationContext(Computer computer, String source) {
        this.computer = Preconditions.checkNotNull(computer, "computer");
        this.source = Preconditions.checkNotNull(source, "source");
    }

    /**
     * @return computer responsible for this invocation
     */
    public Computer getComputer() {
        return computer;
    }

    /**
     * @return source code of the script being executed
     */
    public String getSource() {
        return source;
    }

    /**
     * Returns whether this invocation should terminate and if yes,
     * then it returns the exception responsible for the termination.
     *
     * @return exception responsible for the termination or empty if the script has not been terminated
     */
    public Optional<Throwable> shouldTerminate() {
        synchronized (lock) {
            return Optional.ofNullable(terminationReason);
        }
    }

    /**
     * Terminates the invocation with provided exception.
     * <p>
     * If the script has already been terminated with a different
     * exception before, the call of this method is ignored.
     *
     * @param t exception, supplies empty {@link ScriptTerminatedException} if {@code null}
     */
    public void terminate(@Nullable Throwable t) {
        synchronized (lock) {
            if (terminationReason != null) {
                return;
            }
            terminationReason = t != null ? t : new ScriptTerminatedException();
            var blockingTask = this.blockingTask;
            if (blockingTask != null && !blockingTask.isDone()) {
                blockingTask.cancel(true);
            }
        }
    }

    /**
     * Captures current state of the execution.
     * <p>
     * This state is then used to estimate the memory usage of this invocation.
     *
     * @param continuation continuation
     */
    public void captureState(Continuation continuation) {
        synchronized (lock) {
            this.continuation = Preconditions.checkNotNull(continuation, "continuation");
        }
    }

    /**
     * Estimates the memory usage of this invocation from the last captured state.
     */
    public void recordMemoryUsage() {
        synchronized (lock) {
            if (continuation != null) {
                lastRecordedMemoryUsage = JactlUtils.collectContinuationTree(continuation).stream()
                        .mapToLong(ComputerInvocationContext::computeContinuationSize)
                        .sum();
                continuation = null;
            }
        }
    }

    /**
     * @return the last computed memory usage of this invocation
     * @see #recordMemoryUsage()
     */
    public long getLastRecordedMemoryUsage() {
        return lastRecordedMemoryUsage;
    }

    boolean scheduleBlockingTask(Supplier<Future<?>> scheduleFunction) {
        Preconditions.checkNotNull(scheduleFunction, "scheduleFunction");
        synchronized (lock) {
            // do not schedule the blocking task if the invocation is already terminated
            // this must be done under the lock otherwise in case of a race condition
            // there could be a blocking task waiting while the invocation already ended
            if (shouldTerminate().isPresent()) {
                return false;
            }
            blockingTask = Preconditions.checkNotNull(scheduleFunction.get(), "blockingTask");
            return true;
        }
    }

    void captureCheckpoint(int checkpointId, byte[] checkpoint) {
        Preconditions.checkNotNull(checkpoint, "checkpoint");
        synchronized (lock) {
            if (shouldTerminate().isPresent()) {
                return;
            }
            this.checkpoint.capture(checkpointId, checkpoint);
        }
    }

    Optional<byte[]> getCheckpoint() {
        return checkpoint.get();
    }

    private static long computeContinuationSize(Continuation continuation) {
        Preconditions.checkNotNull(continuation, "continuation");
        long objects = continuation.localObjects != null
                ? SizedObject.sizeOf(Arrays.asList(continuation.localObjects))
                : 0L;
        long primitives = continuation.localPrimitives != null
                ? (long) continuation.localPrimitives.length * (Long.SIZE / Byte.SIZE)
                : 0L;
        return objects + primitives;
    }

}
