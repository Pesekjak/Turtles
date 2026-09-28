package dev.pesek.turtles.computer.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.util.Docs;

@Docs("""
        Usage: destroy
        
        Destroys the computer with all its data.""")
public final class Destroy implements ComputerCommand<Computer> {

    private static final Destroy INSTANCE = new Destroy();

    public static Destroy get() {
        return INSTANCE;
    }

    private final LiteralArgumentBuilder<Computer.Executor<Computer>> node = LiteralArgumentBuilder
            .<Computer.Executor<Computer>>literal("destroy")
            .executes(ctx -> {
                Computer computer = ctx.getSource().computer();
                computer.destroy();
                return SUCCESS;
            });

    private Destroy() {
    }

    @Override
    public LiteralArgumentBuilder<Computer.Executor<Computer>> build() {
        return node;
    }

}
