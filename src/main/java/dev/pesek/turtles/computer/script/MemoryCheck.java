package dev.pesek.turtles.computer.script;

import dev.pesek.turtles.Turtles;

public final class MemoryCheck {

    public static boolean shouldCheckForMemory() {
        return checkMemoryThreshold(Turtles.config().memoryCheckMemoryUsageLimit());
    }

    public static boolean shouldKillScripts() {
        return checkMemoryThreshold(Turtles.config().startKillingScriptsMemoryUsageLimit());
    }

    private static boolean checkMemoryThreshold(int limit) {
        if (limit < 0) {
            return false;
        }
        double threshold = (double) limit / 100;
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        double usage = (double) usedMemory / maxMemory;
        return usage >= threshold;
    }

    private MemoryCheck() {
        throw new UnsupportedOperationException();
    }

}
