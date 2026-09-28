package dev.pesek.turtles.computer.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.util.Colors;
import dev.pesek.turtles.util.Docs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Docs("""
        Usage: cp <source> <target>
        
        Copies already existing files and directories.""")
public final class Cp implements ComputerCommand<Computer> {

    private static final Cp INSTANCE = new Cp();

    public static Cp get() {
        return INSTANCE;
    }

    private final LiteralArgumentBuilder<Computer.Executor<Computer>> node = LiteralArgumentBuilder
            .<Computer.Executor<Computer>>literal("cp")
            .then(RequiredArgumentBuilder.<Computer.Executor<Computer>, String>argument("source",
                            StringArgumentType.string())
                    .then(RequiredArgumentBuilder.<Computer.Executor<Computer>, String>argument("target",
                                    StringArgumentType.greedyString())
                            .executes(ctx -> {
                                Computer computer = ctx.getSource().computer();
                                String sourceStr = ctx.getArgument("source", String.class).trim();
                                String targetStr = ctx.getArgument("target", String.class).trim();

                                Path source = computer.getWorkingDirectory().resolve(sourceStr).normalize();
                                Path target = computer.getWorkingDirectory().resolve(targetStr).normalize();

                                if (!Files.exists(source)) {
                                    computer.print(Colors.RED + "Source file or directory does not exist");
                                    return FAIL;
                                }

                                if (Files.isDirectory(target)) {
                                    target = target.resolve(source.getFileName());
                                }

                                if (Files.exists(target)) {
                                    computer.print(Colors.RED + "Target already exists");
                                    return FAIL;
                                }

                                try {
                                    if (Files.isDirectory(source)) {
                                        Path finalTarget = target;
                                        try (Stream<Path> stream = Files.walk(source)) {
                                            stream.forEach(srcPath -> {
                                                try {
                                                    Path destPath = finalTarget.resolve(source.relativize(srcPath));
                                                    if (Files.isDirectory(srcPath)) {
                                                        if (!Files.exists(destPath)) {
                                                            Files.createDirectory(destPath);
                                                        }
                                                    } else {
                                                        Files.copy(srcPath, destPath);
                                                    }
                                                } catch (IOException e) {
                                                    throw new UncheckedIOException(e);
                                                }
                                            });
                                        }
                                    } else {
                                        Files.copy(source, target);
                                    }
                                    return SUCCESS;
                                } catch (UncheckedIOException e) {
                                    return ComputerCommand.handleCommandError(computer, "Failed to copy", e.getCause());
                                } catch (IOException e) {
                                    return ComputerCommand.handleCommandError(computer, "Failed to copy", e);
                                }
                            })
                    )
            );

    private Cp() {
    }

    @Override
    public LiteralArgumentBuilder<Computer.Executor<Computer>> build() {
        return node;
    }

}
