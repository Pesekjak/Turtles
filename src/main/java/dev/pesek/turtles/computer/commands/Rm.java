package dev.pesek.turtles.computer.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.util.Colors;
import dev.pesek.turtles.util.Docs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

@Docs("""
        Usage:
          rm <path>
          rm <path> force
        
        Removes the provided file or empty directory.
        To remove non-empty directory use the force parameter.""")
public final class Rm implements ComputerCommand<Computer> {

    private static final Rm INSTANCE = new Rm();

    public static Rm get() {
        return INSTANCE;
    }

    private final LiteralArgumentBuilder<Computer.Executor<Computer>> node = LiteralArgumentBuilder
            .<Computer.Executor<Computer>>literal("rm")
            .then(RequiredArgumentBuilder.<Computer.Executor<Computer>, String>argument("path",
                            StringArgumentType.string())
                    .executes(ctx -> executeRm(ctx.getSource().computer(),
                            ctx.getArgument("path", String.class), false))
                    .then(LiteralArgumentBuilder.<Computer.Executor<Computer>>literal("force")
                            .executes(ctx -> executeRm(ctx.getSource().computer(),
                                    ctx.getArgument("path", String.class), true))
                    )
            );

    private Rm() {
    }

    @Override
    public LiteralArgumentBuilder<Computer.Executor<Computer>> build() {
        return node;
    }

    private int executeRm(Computer computer, String path, boolean force) {
        Path resolved = computer.getWorkingDirectory().resolve(path.trim()).normalize();

        if (!Files.exists(resolved)) {
            computer.print(Colors.RED + "File or directory not found");
            return FAIL;
        }

        try {
            if (Files.isDirectory(resolved)) {
                boolean isEmpty;
                try (Stream<Path> entries = Files.list(resolved)) {
                    isEmpty = entries.findFirst().isEmpty();
                }

                if (!isEmpty && !force) {
                    computer.print(Colors.RED + "Directory is not empty. Use 'rm <file> force' to delete");
                    return FAIL;
                }

                if (!isEmpty) {
                    try (Stream<Path> walk = Files.walk(resolved)) {
                        walk.sorted(Comparator.reverseOrder())
                                .forEach(p -> {
                                    try {
                                        Files.delete(p);
                                    } catch (IOException e) {
                                        computer.print(Colors.RED + "Failed to delete: " + p.getFileName());
                                    }
                                });
                    }
                } else {
                    Files.delete(resolved);
                }
            } else {
                Files.delete(resolved);
            }
            return SUCCESS;
        } catch (IOException e) {
            return ComputerCommand.handleCommandError(computer, "Failed to delete", e);
        }
    }

}
