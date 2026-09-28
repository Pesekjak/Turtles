package dev.pesek.turtles.computer.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.util.Docs;

@Docs("""
        Usage: pwd
        
        Prints current working directory.""")
public final class Pwd implements ComputerCommand<Computer> {

    private static final Pwd INSTANCE = new Pwd();

    public static Pwd get() {
        return INSTANCE;
    }

    private final LiteralArgumentBuilder<Computer.Executor<Computer>> node = LiteralArgumentBuilder
            .<Computer.Executor<Computer>>literal("pwd")
            .executes(ctx -> {
                Computer computer = ctx.getSource().computer();
                computer.print(computer.getWorkingDirectory().toAbsolutePath().toString());
                return SUCCESS;
            });

    private Pwd() {
    }

    @Override
    public LiteralArgumentBuilder<Computer.Executor<Computer>> build() {
        return node;
    }

}
