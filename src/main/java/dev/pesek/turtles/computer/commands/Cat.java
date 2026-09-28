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
        Usage: cat <path>
        
        Prints out contents of a file.""")
public final class Cat implements ComputerCommand<Computer> {

    private static final Cat INSTANCE = new Cat();

    public static Cat get() {
        return INSTANCE;
    }

    private final LiteralArgumentBuilder<Computer.Executor<Computer>> node = LiteralArgumentBuilder
            .<Computer.Executor<Computer>>literal("cat")
            .then(RequiredArgumentBuilder.<Computer.Executor<Computer>, String>argument("path",
                            StringArgumentType.greedyString())
                    .executes(ctx -> {
                        Computer computer = ctx.getSource().computer();
                        String path = ctx.getArgument("path", String.class).trim();
                        Path resolved = computer.getWorkingDirectory().resolve(path).normalize();

                        if (!Files.isRegularFile(resolved)) {
                            computer.print(Colors.RED + "File does not exist");
                            return FAIL;
                        }

                        try {
                            String got = Files.readString(resolved);
                            computer.print(got.split("\n"));
                            return SUCCESS;
                        } catch (IOException e) {
                            return ComputerCommand.handleCommandError(computer, "Failed to read file", e);
                        }
                    })
            );

    private Cat() {
    }

    @Override
    public LiteralArgumentBuilder<Computer.Executor<Computer>> build() {
        return node;
    }

}
