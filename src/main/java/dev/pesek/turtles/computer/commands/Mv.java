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
        Usage: mv <source> <target>
        
        Moves a file.""")
public final class Mv implements ComputerCommand<Computer> {

    private static final Mv INSTANCE = new Mv();

    public static Mv get() {
        return INSTANCE;
    }

    private final LiteralArgumentBuilder<Computer.Executor<Computer>> node = LiteralArgumentBuilder
            .<Computer.Executor<Computer>>literal("mv")
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
                                    Files.move(source, target);
                                    return SUCCESS;
                                } catch (IOException e) {
                                    return ComputerCommand.handleCommandError(computer, "Failed to move", e);
                                }
                            })
                    )
            );

    private Mv() {
    }

    @Override
    public LiteralArgumentBuilder<Computer.Executor<Computer>> build() {
        return node;
    }

}
