package dev.pesek.turtles.computer.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.util.Colors;
import dev.pesek.turtles.util.DialogUtils;
import dev.pesek.turtles.util.Docs;
import dev.pesek.turtles.util.JactlHighlighter;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Docs("""
        Usage: less <path>
        
        Opens new window with contents of provided file.
        Provides syntax highlighting for scripts with .jactl extension.""")
public final class Less implements ComputerCommand<Computer> {

    private static final Less INSTANCE = new Less();

    public static Less get() {
        return INSTANCE;
    }

    private final LiteralArgumentBuilder<Computer.Executor<Computer>> node = LiteralArgumentBuilder
            .<Computer.Executor<Computer>>literal("less")
            .then(RequiredArgumentBuilder.<Computer.Executor<Computer>, String>argument("path",
                            StringArgumentType.greedyString())
                    .executes(ctx -> {
                        Computer computer = ctx.getSource().computer();
                        String path = ctx.getArgument("path", String.class).trim();
                        Path resolved = computer.getWorkingDirectory().resolve(path).normalize();

                        if (!Files.exists(resolved)) {
                            computer.print(Colors.RED + "File does not exist");
                            return FAIL;
                        }

                        if (Files.isDirectory(resolved)) {
                            computer.print(Colors.RED + "Cannot view a directory");
                            return FAIL;
                        }

                        Player player = ctx.getSource().player();
                        if (player == null) {
                            computer.print(Colors.RED + "Less command must be executed by a player");
                            return FAIL;
                        }

                        openViewFile(computer, player, resolved);
                        return SUCCESS;
                    })
            );

    private Less() {
    }

    @Override
    public LiteralArgumentBuilder<Computer.Executor<Computer>> build() {
        return node;
    }

    private void openViewFile(Computer computer, Player player, Path path) {
        String content;

        try {
            content = Files.readString(path);
        } catch (IOException e) {
            ComputerCommand.handleCommandError(computer, "Failed to read file for viewing", e);
            return;
        }

        if (path.getFileName().toString().endsWith(".jactl")) {
            content = JactlHighlighter.highlight(content);
        }

        DialogInput viewInput = DialogUtils.viewInput("view_content", content);

        List<ActionButton> buttons = List.of(
                DialogUtils.actionButton("Close", (_, _) -> {
                    if (!computer.addToActiveViewers(player, true)) {
                        player.closeDialog();
                    }
                })
        );

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogUtils.dialogBase(
                        Component.text("Viewing: '" + path.getFileName().toString() + "'"),
                        null, List.of(viewInput)))
                .type(DialogType.multiAction(buttons, null, 5))
        );

        if (computer.removeFromActiveViewers(player, false)) {
            player.showDialog(dialog);
        }
    }

}
