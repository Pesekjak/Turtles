package dev.pesek.turtles.computer.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.util.Docs;

@Docs("""
        Usage: shutdown
        
        Shuts down the computer.""")
public final class Shutdown implements ComputerCommand<Computer> {

    private static final Shutdown INSTANCE = new Shutdown();

    public static Shutdown get() {
        return INSTANCE;
    }

    private final LiteralArgumentBuilder<Computer.Executor<Computer>> node = LiteralArgumentBuilder
            .<Computer.Executor<Computer>>literal("shutdown")
            .executes(ctx -> {
                Computer computer = ctx.getSource().computer();
                computer.shutdown("Turned off");
                return SUCCESS;
            });

    private Shutdown() {
    }

    @Override
    public LiteralArgumentBuilder<Computer.Executor<Computer>> build() {
        return node;
    }

}
