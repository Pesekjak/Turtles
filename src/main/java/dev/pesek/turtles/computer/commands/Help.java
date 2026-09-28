package dev.pesek.turtles.computer.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.util.Docs;

import java.util.Arrays;

@Docs("""
        Usage: help
        
        Prints the help screen.""")
public final class Help implements ComputerCommand<Computer> {

    private static final Help INSTANCE = new Help();

    public static Help get() {
        return INSTANCE;
    }

    private final LiteralArgumentBuilder<Computer.Executor<Computer>> node = LiteralArgumentBuilder
            .<Computer.Executor<Computer>>literal("help")
            .executes(ctx -> {
                Computer computer = ctx.getSource().computer();
                computer.print(computer.getHelp());
                return SUCCESS;
            });

    private Help() {
    }

    @Override
    public LiteralArgumentBuilder<Computer.Executor<Computer>> build() {
        return node;
    }

}
