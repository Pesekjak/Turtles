package dev.pesek.turtles.computer.event;

import com.google.common.base.Preconditions;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.computer.commands.ComputerCommand;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ComputerCommandsInitEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Computer computer;
    private final List<ComputerCommand<Computer>> commands;

    @ApiStatus.Internal
    public ComputerCommandsInitEvent(Computer computer, List<ComputerCommand<Computer>> commands) {
        this.computer = Preconditions.checkNotNull(computer, "computer");
        this.commands = Preconditions.checkNotNull(commands, "commands");
    }

    public Computer getComputer() {
        return computer;
    }

    public /* modifiable */ List<ComputerCommand<Computer>> getCommands() {
        return commands;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

}
