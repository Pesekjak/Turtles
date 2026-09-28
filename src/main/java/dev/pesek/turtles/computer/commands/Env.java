package dev.pesek.turtles.computer.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.computer.EnvVar;
import dev.pesek.turtles.util.Colors;
import dev.pesek.turtles.util.Docs;

import java.util.Collection;

@Docs("""
        Usage:
          env
          env get <name>
          env set <name>
        
        Allows you to list, read and write computer environment variables.""")
public final class Env implements ComputerCommand<Computer> {

    private static final Env INSTANCE = new Env();

    public static Env get() {
        return INSTANCE;
    }

    private final LiteralArgumentBuilder<Computer.Executor<Computer>> node = LiteralArgumentBuilder
            .<Computer.Executor<Computer>>literal("env")
            .executes(ctx -> {
                Computer computer = ctx.getSource().computer();
                Collection<EnvVar<?>> envVars = computer.getEnvironmentVariables();
                if (envVars.isEmpty()) {
                    computer.print("No environment variables found");
                } else {
                    for (EnvVar<?> envVar : envVars) {
                        computer.print(envVar.getName() + "=" + envVar.get());
                    }
                }
                return SUCCESS;
            })
            .then(LiteralArgumentBuilder.<Computer.Executor<Computer>>literal("get")
                    .then(RequiredArgumentBuilder.<Computer.Executor<Computer>, String>argument("name",
                                    StringArgumentType.word())
                            .executes(ctx -> {
                                Computer computer = ctx.getSource().computer();
                                String name = ctx.getArgument("name", String.class);

                                EnvVar<?> envVar = computer.getEnvironmentVariable(name).orElse(null);
                                if (envVar == null) {
                                    computer.print(Colors.RED + "Environment variable '" + name + "' not found");
                                    return FAIL;
                                }

                                computer.print(name + "=" + envVar.get());
                                return SUCCESS;
                            })
                    )
            )
            .then(LiteralArgumentBuilder.<Computer.Executor<Computer>>literal("set")
                    .then(RequiredArgumentBuilder.<Computer.Executor<Computer>, String>argument("name",
                                    StringArgumentType.word())
                            .then(RequiredArgumentBuilder.<Computer.Executor<Computer>, String>argument("value",
                                            StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        Computer computer = ctx.getSource().computer();
                                        String name = ctx.getArgument("name", String.class);
                                        String valueStr = ctx.getArgument("value", String.class).trim();

                                        EnvVar<?> envVar = computer.getEnvironmentVariable(name).orElse(null);
                                        if (envVar == null) {
                                            computer.print(Colors.RED + "Environment variable '" + name + "' not found");
                                            return FAIL;
                                        }

                                        if (!envVar.isModifiable()) {
                                            computer.print(Colors.RED + "Environment variable '" + name + "' is read-only");
                                            return FAIL;
                                        }

                                        try {
                                            parseAndSet(envVar, valueStr);
                                            computer.print("Set " + name + "=" + envVar.get());
                                            return SUCCESS;
                                        } catch (CommandSyntaxException e) {
                                            computer.print(Colors.RED + "Invalid format: " + e.getRawMessage().getString());
                                            return FAIL;
                                        } catch (Exception e) {
                                            return ComputerCommand.handleCommandError(computer, "Failed to set "
                                                    + "environment variable", e);
                                        }
                                    })
                            )
                    )
            );

    private Env() {
    }

    @Override
    public LiteralArgumentBuilder<Computer.Executor<Computer>> build() {
        return node;
    }

    private <V> void parseAndSet(EnvVar<V> envVar, String valueStr) throws CommandSyntaxException {
        V parsedValue = envVar.parse(valueStr);
        envVar.set(parsedValue);
    }

}
