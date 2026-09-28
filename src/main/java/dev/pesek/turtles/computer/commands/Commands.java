package dev.pesek.turtles.computer.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.util.Colors;
import dev.pesek.turtles.util.Docs;

import java.util.List;
import java.util.stream.Collectors;

@Docs("""
        Usage:
          commands
          commands <cmd>
        
        Displays list of available commands or their documentation""")
public final class Commands implements ComputerCommand<Computer> {

    private static final Commands INSTANCE = new Commands();

    public static Commands get() {
        return INSTANCE;
    }

    private final LiteralArgumentBuilder<Computer.Executor<Computer>> node = LiteralArgumentBuilder
            .<Computer.Executor<Computer>>literal("commands")
            .executes(ctx -> {
                Computer computer = ctx.getSource().computer();
                List<ComputerCommand.Info> commands = computer.getRegisteredCommands();

                String joinedCommands = commands.stream()
                        .map(ComputerCommand.Info::label)
                        .sorted()
                        .collect(Collectors.joining(" "));

                computer.print("Available commands:\n" + joinedCommands);
                return SUCCESS;
            })
            .then(RequiredArgumentBuilder.<Computer.Executor<Computer>, String>argument("cmd", StringArgumentType.word())
                    .executes(ctx -> {
                        Computer computer = ctx.getSource().computer();
                        String targetCmd = ctx.getArgument("cmd", String.class).toLowerCase().trim();

                        computer.getRegisteredCommands().stream()
                                .filter(info -> info.label().equalsIgnoreCase(targetCmd))
                                .findFirst()
                                .ifPresentOrElse(
                                        info -> {
                                            String description = info.description() != null
                                                    ? info.description()
                                                    : "No documentation available.";

                                            computer.print(Colors.GREEN + info.label() + Colors.RESET + "\n"
                                                    + description);
                                        },
                                        () -> computer.print(Colors.RED + "Unknown command: '" + targetCmd + "'")
                                );
                        return SUCCESS;
                    })
            );

    private Commands() {
    }

    @Override
    public LiteralArgumentBuilder<Computer.Executor<Computer>> build() {
        return node;
    }

}
