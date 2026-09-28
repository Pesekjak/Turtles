package dev.pesek.turtles.util;

import com.google.common.base.Preconditions;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DialogUtils {

    /**
     * Returns dialog base with common properties that allow for
     * smooth dialog refreshing.
     *
     * @param title title of the dialog
     * @param inputs inputs
     * @param body body
     * @return dialog base
     */
    public static DialogBase dialogBase(Component title, @Nullable List<? extends DialogBody> body,
                                        @Nullable List<? extends DialogInput> inputs) {
        Preconditions.checkNotNull(title, "title");
        return DialogBase.builder(title)
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .pause(false)
                .body(body != null ? body : List.of())
                .inputs(inputs != null ? inputs : List.of())
                .build();
    }

    /**
     * Creates action button commonly used on computer screens.
     *
     * @param name name of the action
     * @param onClick click listener
     * @return action button
     */
    public static ActionButton actionButton(String name, DialogActionCallback onClick) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(onClick, "onClick");
        return ActionButton.builder(Component.text(name))
                .width(100)
                .action(DialogAction.customClick(onClick, ClickCallback.Options.builder()
                        .uses(ClickCallback.UNLIMITED_USES)
                        .build()))
                .build();
    }

    /**
     * Creates multi action dialog type that supports listening
     * to the dialog close (by ESC).
     *
     * @param actionButtons action buttons
     * @param onClose on close listener
     * @param columns number of columns of action buttons
     * @return dialog type
     */
    public static DialogType multiAction(List<ActionButton> actionButtons,
                                         @Nullable DialogActionCallback onClose, int columns) {
        Preconditions.checkNotNull(actionButtons, "actionButtons");
        ActionButton closeHandler = ActionButton.builder(Component.empty())
                .width(1)
                .action(DialogAction.customClick((responseView, audience) -> {
                    if (onClose != null) {
                        //noinspection OverrideOnly
                        onClose.accept(responseView, audience);
                    }
                    audience.closeDialog();
                }, ClickCallback.Options.builder().build()))
                .build();
        return DialogType.multiAction(actionButtons, closeHandler, columns);
    }

    public static DialogInput viewInput(String name, String initialContent) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(initialContent, "initialContent");
        return DialogInput.text(name, Component.empty())
                .maxLength(Integer.MAX_VALUE)
                .width(380)
                .multiline(TextDialogInput.MultilineOptions
                        .create(Integer.MAX_VALUE, 200))
                .initial(initialContent)
                .build();
    }

    private DialogUtils() {
        throw new UnsupportedOperationException();
    }

}
