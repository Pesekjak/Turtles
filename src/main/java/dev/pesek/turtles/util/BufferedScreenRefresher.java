package dev.pesek.turtles.util;

import com.google.common.base.Preconditions;
import dev.pesek.turtles.computer.Computer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class BufferedScreenRefresher implements Runnable {

    private static final ScheduledExecutorService EXECUTOR_SERVICE = Executors
            .newScheduledThreadPool(0, Thread.ofVirtual().factory());

    private final Computer computer;
    private final AtomicBoolean buffering = new AtomicBoolean(false);

    public BufferedScreenRefresher(Computer computer) {
        this.computer = Preconditions.checkNotNull(computer, "computer");
    }

    @Override
    public void run() {
        if (!buffering.compareAndSet(false, true)) {
            return;
        }
        EXECUTOR_SERVICE.schedule(() -> {
            buffering.set(false);
            computer.refreshScreen();
        }, 500, TimeUnit.MILLISECONDS);
    }

}
