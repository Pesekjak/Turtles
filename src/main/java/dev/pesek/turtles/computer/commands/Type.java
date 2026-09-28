package dev.pesek.turtles.computer.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.util.Docs;

import java.nio.file.Files;
import java.nio.file.Path;

@Docs("""
        Usage: type <path>
        
        Prints out type (file or directory) at given path.""")
public final class Type implements ComputerCommand<Computer> {

    private static final Type INSTANCE = new Type();

    public static Type get() {
        return INSTANCE;
    }

    private final LiteralArgumentBuilder<Computer.Executor<Computer>> node = LiteralArgumentBuilder
            .<Computer.Executor<Computer>>literal("type")
            .then(RequiredArgumentBuilder.<Computer.Executor<Computer>, String>argument("path",
                            StringArgumentType.greedyString())
                    .executes(ctx -> {
                        Computer computer = ctx.getSource().computer();
                        String path = ctx.getArgument("path", String.class).trim();
                        Path resolved = computer.getWorkingDirectory().resolve(path).normalize();

                        if (!Files.exists(resolved)) {
                            computer.print("not found");
                        } else if (Files.isDirectory(resolved)) {
                            computer.print("directory");
                        } else if (Files.isRegularFile(resolved)) {
                            computer.print("file");
                        } else {
                            computer.print("unknown");
                        }

                        return SUCCESS;
                    })
            );

    private Type() {
    }

    @Override
    public LiteralArgumentBuilder<Computer.Executor<Computer>> build() {
        return node;
    }

}
