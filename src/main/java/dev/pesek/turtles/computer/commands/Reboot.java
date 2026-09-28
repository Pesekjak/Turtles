package dev.pesek.turtles.computer.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.util.Docs;

@Docs("""
        Usage: reboot
        
        Reboots the computer.""")
public final class Reboot implements ComputerCommand<Computer> {

    private static final Reboot INSTANCE = new Reboot();

    public static Reboot get() {
        return INSTANCE;
    }

    private final LiteralArgumentBuilder<Computer.Executor<Computer>> node = LiteralArgumentBuilder
            .<Computer.Executor<Computer>>literal("reboot")
            .executes(ctx -> {
                Computer computer = ctx.getSource().computer();
                computer.reboot();
                return SUCCESS;
            });

    private Reboot() {
    }

    @Override
    public LiteralArgumentBuilder<Computer.Executor<Computer>> build() {
        return node;
    }

}
