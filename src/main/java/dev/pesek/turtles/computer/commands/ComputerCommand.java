package dev.pesek.turtles.computer.commands;

import com.google.common.base.Preconditions;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.pesek.turtles.computer.Computer;
import org.jetbrains.annotations.Nullable;

/**
 * Command that can be executed by a {@link Computer}.
 *
 * @param <T> type of the computer this command can be executed by
 * @apiNote to add documentation, use {@link dev.pesek.turtles.util.Docs}.
 */
public interface ComputerCommand<T extends Computer> {

    /**
     * Command execution failed.
     */
    int FAIL = 0;

    /**
     * Command execution succeeded.
     */
    int SUCCESS = 1;

    LiteralArgumentBuilder<Computer.Executor<T>> build();

    static int handleCommandError(Computer computer, String prefix, Throwable e) {
        computer.printError(prefix, e);
        return FAIL;
    }

    record Info(String label, @Nullable String description) {

        public Info {
            Preconditions.checkNotNull(label, "label");
        }

    }

}
