package dev.pesek.turtles.computer.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.util.Colors;
import dev.pesek.turtles.util.Docs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Docs("""
        Usage: status
        
        Displays computer information.""")
public final class Status implements ComputerCommand<Computer> {

    private static final Status INSTANCE = new Status();

    public static Status get() {
        return INSTANCE;
    }

    private final LiteralArgumentBuilder<Computer.Executor<Computer>> node = LiteralArgumentBuilder
            .<Computer.Executor<Computer>>literal("status")
            .executes(ctx -> {
                Computer computer = ctx.getSource().computer();

                long usedSpace = 0;
                Iterable<Path> roots = computer.getWorkingDirectory().getFileSystem().getRootDirectories();
                for (Path root : roots) {
                    try (Stream<Path> paths = Files.walk(root)) {
                        usedSpace += paths.filter(Files::isRegularFile)
                                .mapToLong(p -> {
                                    try {
                                        return Files.size(p);
                                    } catch (IOException e) {
                                        return 0;
                                    }
                                }).sum();
                    } catch (IOException e) {
                        ComputerCommand.handleCommandError(computer, "Failed to calculate file system usage", e);
                        return FAIL;
                    }
                }
                long maxFs = computer.getFileSystemSize();

                computer.print("Storage: " + formatBytes(usedSpace) + " / " + formatBytes(maxFs));
                computer.print("Script Memory Limit: " + (computer.getMaxScriptMemoryUsage() >= 0
                        ? formatBytes(computer.getMaxScriptMemoryUsage())
                        : "DISABLED"));
                computer.print("Max Script Length: " + (computer.getMaxScriptLength() >= 0
                        ? computer.getMaxScriptLength()
                        : "DISABLED"));
                computer.print("Execution Timeout: " + (computer.getMaxScriptExecutionTime() >= 0
                        ? computer.getMaxScriptExecutionTime() + " ms"
                        : "DISABLED"
                ));
                computer.print("Max Loop Iterations: " + (computer.getMaxScriptIterations() >= 0
                        ? computer.getMaxScriptIterations()
                        : "DISABLED"
                ));

                return SUCCESS;
            });

    private Status() {
    }

    @Override
    public LiteralArgumentBuilder<Computer.Executor<Computer>> build() {
        return node;
    }

    /**
     * Utility method to format bytes into human-readable strings (KB, MB, GB).
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.2f %s", bytes / Math.pow(1024, exp), pre);
    }
}
