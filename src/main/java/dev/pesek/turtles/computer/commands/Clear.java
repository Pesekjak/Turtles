package dev.pesek.turtles.computer.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.util.Docs;

import java.util.Arrays;

@Docs("""
        Usage: clear
        
        Clears the computer screen.""")
public final class Clear implements ComputerCommand<Computer> {

    private static final Clear INSTANCE = new Clear();

    public static Clear get() {
        return INSTANCE;
    }

    private final LiteralArgumentBuilder<Computer.Executor<Computer>> node = LiteralArgumentBuilder
            .<Computer.Executor<Computer>>literal("clear")
            .executes(ctx -> {
                Computer computer = ctx.getSource().computer();
                String[] emptyLines = new String[computer.getScreen().getHeight()];
                Arrays.fill(emptyLines, "");
                computer.getScreen().print(emptyLines);
                return SUCCESS;
            });

    private Clear() {
    }

    @Override
    public LiteralArgumentBuilder<Computer.Executor<Computer>> build() {
        return node;
    }

}
