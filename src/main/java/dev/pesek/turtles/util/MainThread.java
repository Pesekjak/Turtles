package dev.pesek.turtles.util;

import com.google.common.base.Preconditions;
import dev.pesek.turtles.Turtles;
import org.bukkit.Bukkit;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class MainThread {

    public static void ensure() {
        Preconditions.checkState(Bukkit.isPrimaryThread(), "Must be called on the main thread");
    }

    public static Executor executor() {
        return Bukkit.getScheduler().getMainThreadExecutor(Turtles.getInstance());
    }

    public static CompletableFuture<Void> run(Runnable runnable) {
        return run(() -> {
            runnable.run();
            return null;
        });
    }

    public static <T> CompletableFuture<T> run(Callable<T> callable) {
        CompletableFuture<T> future = new CompletableFuture<>();

        if (Bukkit.isPrimaryThread()) {
            try {
                future.complete(callable.call());
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        } else {
            Bukkit.getScheduler().runTask(Turtles.getInstance(), () -> {
                try {
                    future.complete(callable.call());
                } catch (Exception exception) {
                    future.completeExceptionally(exception);
                }
            });
        }

        return future;
    }

    public static CompletableFuture<Void> runLater(Runnable runnable, int ticks) {
        return runLater(() -> {
            runnable.run();
            return null;
        }, ticks);
    }

    public static <T> CompletableFuture<T> runLater(Callable<T> callable, int ticks) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskLater(Turtles.getInstance(), () -> {
            try {
                future.complete(callable.call());
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        }, ticks);
        return future;
    }

    private MainThread() {
        throw new UnsupportedOperationException();
    }

}
