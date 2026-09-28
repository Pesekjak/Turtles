package dev.pesek.turtles.computer.event;

import com.google.common.base.Preconditions;
import dev.pesek.turtles.computer.Computer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.file.FileSystem;

public final class ComputerFilesInitEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Computer computer;
    private final FileSystem fileSystem;

    @ApiStatus.Internal
    public ComputerFilesInitEvent(Computer computer) {
        this.computer = Preconditions.checkNotNull(computer, "computer");
        fileSystem = computer.getFileSystem();
    }

    public Computer getComputer() {
        return computer;
    }

    public FileSystem getFileSystem() {
        return fileSystem;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

}
