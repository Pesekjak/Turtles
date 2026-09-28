package dev.pesek.turtles;

import com.google.common.base.Preconditions;
import de.exlll.configlib.Comment;

public record TurtlesConfig(
        @Comment("Default size of the computer file system in bytes.")
        long computerFileSystemSize,

        @Comment({"",
                "Max length of the script executed by the computer.",
                "Set to -1 to disable.",
                "On public servers keep in mind that players may be hostile."})
        long maxScriptLength,

        @Comment({"",
                "Max number of iterations a script can perform without yielding,",
                "guarding against tight loops that would otherwise spin.",
                "Set to -1 to disable.",
                "Either maxScriptIterations or maxScriptExecTime should ALWAYS be set!",
                "On public servers keep in mind that players may be hostile."})
        long maxScriptIterations,

        @Comment({"",
                "Max time in ms script can run. Keep in mind this option can make",
                "automatic \"forever\" farms/setups impossible.",
                "Set to -1 to disable.",
                "Either maxScriptIterations or maxScriptExecTime should ALWAYS be set!",
                "On public servers keep in mind that players may be hostile."})
        int maxScriptExecTime,

        @Comment({"",
                "How frequently to check for the maxScriptExecTime while in a loop (e.g. every 100 iterations)"})
        int loopTimeoutFreqCheck,

        @Comment({"",
                "Memory limit for a single script.",
                "Set to -1 to disable.",
                "On public servers keep in mind that players may be hostile."})
        long maxScriptMemoryUsage,

        @Comment({"",
                "At what server memory usage in percentages the memory checks on running scripts should",
                "be performed. Computing the size of the computers allocated resources",
                "is intensive task that can slow performance of ALL computers and should be done",
                "only when server is running low on free memory.",
                "Set to -1 to disable."})
        int memoryCheckMemoryUsageLimit,

        @Comment({"",
                "At what server memory usage in percentages the scripts should start shutting down.",
                "This should be the last resort to free up memory before server crashes.",
                "Set to -1 to disable."})
        int startKillingScriptsMemoryUsageLimit,

        @Comment({"",
                "Max number of running computers per player.",
                "Computers can be heavy on server resources.",
                "Set to -1 to disable.",
                "On public servers keep in mind that players may be hostile."})
        int maxRunningComputersPerPlayer,

        @Comment({"",
                "When a computer is placed, it is assigned an owner if",
                "the action was performed by a player.",
                "Max number of running computers per player can be",
                "limited with maxRunningComputersPerPlayer.",
                "If the computer has no owner, this option",
                "disables such computer from booting up.",
                "On public servers keep in mind that players may be hostile."})
        boolean allowComputersWithoutOwners,

        @Comment({"",
                "Number of threads used for the script environment event loop."})
        int scriptEnvironmentEventLoopThreadCount
) {

    public TurtlesConfig() {
        this(
                256 * 1024,
                1 << 16,
                100,
                -1,
                100,
                1024 * 1024,
                85,
                95,
                10,
                false,
                1
        );
    }

    public TurtlesConfig {
        Preconditions.checkState(computerFileSystemSize >= 0, "computerFileSystemSize must be >= 0");
        Preconditions.checkState(loopTimeoutFreqCheck > 0, "loopTimeoutFreqCheck must be > 0");
        Preconditions.checkState(scriptEnvironmentEventLoopThreadCount > 0, "scriptEnvironmentEventLoopThreadCount must be > 0");
    }

}
