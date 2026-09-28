package dev.pesek.turtles.computer.script;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

class Checkpoint {

    private volatile int checkpointId = Integer.MIN_VALUE;
    private volatile byte @Nullable [] lastCheckpoint;

    private final Object lock = new Object();

    void capture(int checkpointId, byte[] checkpoint) {
        Preconditions.checkNotNull(checkpoint, "checkpoint");
        synchronized (lock) {
            if (checkpointId <= this.checkpointId) {
                return;
            }
            this.checkpointId = checkpointId;
            lastCheckpoint = Arrays.copyOf(checkpoint, checkpoint.length);
        }
    }

    Optional<byte[]> get() {
        synchronized (lock) {
            byte[] lastCheckpoint = this.lastCheckpoint;
            if (lastCheckpoint == null) {
                return Optional.empty();
            }
            return Optional.of(Arrays.copyOf(lastCheckpoint, lastCheckpoint.length));
        }
    }

}
