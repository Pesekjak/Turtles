package dev.pesek.turtles.computer.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.util.Colors;
import dev.pesek.turtles.util.Docs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Docs("""
        Usage:
          ls
          ls list
        
        Lists contents of current working directory.""")
public final class Ls implements ComputerCommand<Computer> {

    private static final Ls INSTANCE = new Ls();

    public static Ls get() {
        return INSTANCE;
    }

    private final LiteralArgumentBuilder<Computer.Executor<Computer>> node = LiteralArgumentBuilder
            .<Computer.Executor<Computer>>literal("ls")
            .executes(ctx -> {
                Computer computer = ctx.getSource().computer();
                try (Stream<Path> stream = Files.list(computer.getWorkingDirectory())) {
                    List<String> ordered = stream
                            .sorted(Comparator.comparing(Path::getFileName))
                            .map(path -> (Files.isDirectory(path) ? Colors.BLUE : Colors.DARK_GREEN)
                                    + path.getFileName().toString() + Colors.RESET).toList();
                    if (!ordered.isEmpty()) {
                        computer.print(String.join(" ", ordered));
                    }
                    return SUCCESS;
                } catch (IOException e) {
                    return ComputerCommand.handleCommandError(computer, "Failed to list files", e);
                }
            })
            .then(LiteralArgumentBuilder.<Computer.Executor<Computer>>literal("list")
                    .executes(ctx -> {
                        Computer computer = ctx.getSource().computer();
                        try (Stream<Path> stream = Files.list(computer.getWorkingDirectory())) {
                            List<Path> orderedPaths = stream.sorted(Comparator.comparing(Path::getFileName)).toList();
                            computer.print("total: " + orderedPaths.size());
                            for (Path path : orderedPaths) {
                                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                                String name = path.getFileName().toString();
                                String type = attrs.isDirectory() ? "d" : "-";
                                String size = String.format("%5d", attrs.size());
                                String coloredName = (attrs.isDirectory() ? Colors.BLUE : Colors.DARK_GREEN)
                                        + name + Colors.RESET;
                                computer.print(String.format("%s %s %s", type, size, coloredName));
                            }
                            return SUCCESS;
                        } catch (IOException e) {
                            return ComputerCommand.handleCommandError(computer, "Failed to list files", e);
                        }
                    })
            );

    private Ls() {
    }

    @Override
    public LiteralArgumentBuilder<Computer.Executor<Computer>> build() {
        return node;
    }

}
