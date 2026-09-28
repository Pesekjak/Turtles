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

@Docs("""
        Usage: mkdir <path>
        
        Creates new directory.
        If the parent directories do not exist, they are created as well.""")
public final class Mkdir implements ComputerCommand<Computer> {

    private static final Mkdir INSTANCE = new Mkdir();

    public static Mkdir get() {
        return INSTANCE;
    }

    private final LiteralArgumentBuilder<Computer.Executor<Computer>> node = LiteralArgumentBuilder
            .<Computer.Executor<Computer>>literal("mkdir")
            .then(RequiredArgumentBuilder.<Computer.Executor<Computer>, String>argument("path",
                            StringArgumentType.greedyString())
                    .executes(ctx -> {
                        Computer computer = ctx.getSource().computer();
                        String path = ctx.getArgument("path", String.class).trim();
                        Path resolved = computer.getWorkingDirectory().resolve(path).normalize();

                        if (Files.exists(resolved)) {
                            computer.print(Colors.RED + "File or directory with this name already exists");
                            return FAIL;
                        }

                        try {
                            Files.createDirectories(resolved);
                            return SUCCESS;
                        } catch (IOException e) {
                            return ComputerCommand.handleCommandError(computer, "Failed to create directory", e);
                        }
                    })
            );

    private Mkdir() {
    }

    @Override
    public LiteralArgumentBuilder<Computer.Executor<Computer>> build() {
        return node;
    }

}
