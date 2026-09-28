package dev.pesek.turtles.computer.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.util.Colors;
import dev.pesek.turtles.util.Docs;
import io.jactl.CompileError;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Docs("""
        Usage: run <path>
        
        Runs Jactl script.""")
public final class Run implements ComputerCommand<Computer> {

    private static final Run INSTANCE = new Run();

    public static Run get() {
        return INSTANCE;
    }

    private final LiteralArgumentBuilder<Computer.Executor<Computer>> node = LiteralArgumentBuilder
            .<Computer.Executor<Computer>>literal("run")
            .then(RequiredArgumentBuilder.<Computer.Executor<Computer>, String>argument("path", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        Computer computer = ctx.getSource().computer();
                        String path = ctx.getArgument("path", String.class).trim();
                        Path resolved = computer.getWorkingDirectory().resolve(path).normalize();

                        if (!Files.isRegularFile(resolved)) {
                            computer.print(Colors.RED + "File does not exist or is a directory");
                            return FAIL;
                        }

                        try {
                            String code = Files.readString(resolved);
                            computer.runScript(code);
                            return SUCCESS;
                        } catch (IOException e) {
                            return ComputerCommand.handleCommandError(computer, "Failed to read the script file", e);
                        } catch (CompileError e) {
                            return ComputerCommand.handleCommandError(computer, "Failed to compile the script file", e);
                        } catch (Exception e) {
                            return ComputerCommand.handleCommandError(computer, "Failed to run the script file", e);
                        }
                    })
            );

    private Run() {
    }

    @Override
    public LiteralArgumentBuilder<Computer.Executor<Computer>> build() {
        return node;
    }

}
