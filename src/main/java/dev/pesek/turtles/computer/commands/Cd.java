package dev.pesek.turtles.computer.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.util.Colors;
import dev.pesek.turtles.util.Docs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

@Docs("""
        Usage: cd <path>
        
        Changes current working directory.""")
public final class Cd implements ComputerCommand<Computer> {

    private static final Cd INSTANCE = new Cd();

    public static Cd get() {
        return INSTANCE;
    }

    private final LiteralArgumentBuilder<Computer.Executor<Computer>> node = LiteralArgumentBuilder
            .<Computer.Executor<Computer>>literal("cd")
            .executes(ctx -> executeCd(ctx.getSource().computer(), Computer.HOME_PATH))
            .then(RequiredArgumentBuilder.<Computer.Executor<Computer>, String>argument("path",
                            StringArgumentType.greedyString())
                    .executes(ctx -> executeCd(ctx.getSource().computer(),
                            ctx.getArgument("path", String.class)))
            );

    private Cd() {
    }

    @Override
    public LiteralArgumentBuilder<Computer.Executor<Computer>> build() {
        return node;
    }

    private int executeCd(Computer computer, String path) {
        AtomicInteger result = new AtomicInteger(SUCCESS);
        computer.updateWorkingDirectory(prev -> {
            Path resolved = prev.resolve(path.trim()).normalize();
            if (!Files.isDirectory(resolved)) {
                computer.print(Colors.RED + "This directory does not exist");
                result.set(FAIL);
                return prev;
            }
            return resolved;
        });
        return result.get();
    }

}
