package dev.pesek.turtles.computer;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import dev.pesek.turtles.Turtles;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class ComputerManager implements Listener {

    private static final ComputerManager INSTANCE = new ComputerManager();
    static final UUID NO_OWNER_UUID = new UUID(0, 0);

    public static ComputerManager get() {
        return INSTANCE;
    }

    public sealed interface ComputerRequest<T extends Computer> {
        record Success<T extends Computer>(T computer) implements ComputerRequest<T> {
            public Success {
                Preconditions.checkNotNull(computer, "computer");
            }
        }
        record Fail<T extends Computer>(@Nullable String reason) implements ComputerRequest<T> {
        }
    }

    private final Map<UUID, Computer> loadedComputers = new HashMap<>();
    private final Multimap<UUID, Computer> loadedPlayerComputers = HashMultimap.create();

    private final Object lock = new Object();

    private ComputerManager() {
    }

    public <T extends Computer> ComputerRequest<T> create(@Nullable UUID ownerId, Supplier<T> supplier)
            throws IOException {
        return create(ownerId, supplier, null);
    }

    /**
     * Creates and initializes new computer instance.
     *
     * @param ownerId uuid of the player owner of this computer
     * @param supplier supplier of the computer instance.
     * @param pdc data container of the computer if it was previously initialized and serialized
     * @return initialized computer instance
     * @param <T> computer type
     * @throws IOException if file system initialization/deserialization fails
     */
    public <T extends Computer> ComputerRequest<T> create(@Nullable UUID ownerId, Supplier<T> supplier,
                                                          @Nullable PersistentDataContainer pdc) throws IOException {
        Preconditions.checkNotNull(supplier, "supplier");
        synchronized (lock) {
            if (ownerId != null
                    && Turtles.config().maxRunningComputersPerPlayer() >= 0
                    && loadedPlayerComputers.get(ownerId).size() >= Turtles.config().maxRunningComputersPerPlayer()) {
                return new ComputerRequest.Fail<>("There are too many running computers owned by "
                        + "the same player");
            }

            if (ownerId == null && !Turtles.config().allowComputersWithoutOwners()) {
                return new ComputerRequest.Fail<>("This computer does not have an owner");
            }

            T computer = supplier.get();
            computer.ownerId.set(ownerId != null ? ownerId : NO_OWNER_UUID);

            if (pdc != null) {
                computer.deserialize(pdc);
            } else {
                computer.initEnvironment();
                computer.initFileSystem(computer.getFileSystem());
            }

            computer.initDispatcher();
            computer.postInit();

            loadedComputers.put(computer.getUid(), computer);
            if (ownerId != null) {
                loadedPlayerComputers.put(ownerId, computer);
            }
            return new ComputerRequest.Success<>(computer);
        }
    }

    public Optional<Computer> getComputerByUid(UUID uid) {
        synchronized (lock) {
            return Optional.ofNullable(loadedComputers.get(uid));
        }
    }

    void close(Computer computer) {
        Preconditions.checkNotNull(computer, "computer");
        synchronized (lock) {
            loadedComputers.remove(computer.getUid());
            computer.getOwner().ifPresent(ownerId -> loadedPlayerComputers.remove(ownerId, computer));
        }
    }

}
