package dev.pesek.turtles.computer.commands;

import com.google.common.base.Preconditions;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.util.Colors;
import dev.pesek.turtles.util.DialogUtils;
import dev.pesek.turtles.util.Docs;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Docs("""
        Usage: edit <path>
        
        Opens a file in the text editor.
        If the file does not exist, a new empty file is created.""")
public final class Edit implements ComputerCommand<Computer> {

    private static final Edit INSTANCE = new Edit();

    public static Edit get() {
        return INSTANCE;
    }

    private final LiteralArgumentBuilder<Computer.Executor<Computer>> node = LiteralArgumentBuilder
            .<Computer.Executor<Computer>>literal("edit")
            .then(RequiredArgumentBuilder.<Computer.Executor<Computer>, String>argument("path",
                            StringArgumentType.greedyString())
                    .executes(ctx -> {
                        Computer computer = ctx.getSource().computer();
                        String path = ctx.getArgument("path", String.class).trim();
                        Path resolved = computer.getWorkingDirectory().resolve(path).normalize();

                        if (Files.isDirectory(resolved)) {
                            computer.print(Colors.RED + "Only regular files can be edited");
                            return FAIL;
                        }

                        Player player = ctx.getSource().player();
                        if (player == null) {
                            computer.print(Colors.RED + "Edit command must be executed by a player");
                            return FAIL;
                        }

                        openEditFile(computer, player, resolved);
                        return SUCCESS;
                    })
            );

    private Edit() {
    }

    @Override
    public LiteralArgumentBuilder<Computer.Executor<Computer>> build() {
        return node;
    }

    private void openEditFile(Computer computer, Player player, Path path) {
        String initialContent = "";

        if (Files.exists(path)) {
            try {
                initialContent = Files.readString(path);
            } catch (IOException e) {
                ComputerCommand.handleCommandError(computer, "Failed to read file for editing", e);
                return;
            }
        }

        DialogInput editInput = DialogUtils.viewInput("edit_content", initialContent);

        List<ActionButton> buttons = List.of(
                DialogUtils.actionButton("Save", (response, _) -> {
                    String newContent = Preconditions.checkNotNull(response.getText("edit_content"), "edit_content");
                    try {
                        Files.writeString(path, newContent);
                    } catch (IOException e) {
                        ComputerCommand.handleCommandError(computer, "Failed to save file", e);
                    }
                    if (!computer.addToActiveViewers(player, true)) {
                        player.closeDialog();
                    }
                }),
                DialogUtils.actionButton("Discard", (_, _) -> {
                    if (!computer.addToActiveViewers(player, true)) {
                        player.closeDialog();
                    }
                })
        );

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogUtils.dialogBase(
                        Component.text("Editing: '" + path.getFileName().toString() + "'"),
                        null, List.of(editInput)))
                .type(DialogType.multiAction(buttons, null, 5))
        );

        if (computer.removeFromActiveViewers(player, false)) {
            player.showDialog(dialog);
        }
    }

}
