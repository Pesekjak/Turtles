package dev.pesek.turtles.blocks;

import com.google.common.base.Preconditions;
import dev.pesek.turtles.computer.ComputerManager;
import dev.pesek.turtles.computer.PhysicalComputer;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public abstract class MovingComputerBlock<T extends PhysicalComputer<T, B>, B extends MovingComputerBlock<T, B>>
        extends ComputerBlock<T, B> {

    BlockHolder<T, B> holder;

    public MovingComputerBlock(@NotNull Block block, @NotNull BlockCreateContext context) throws IOException {
        super(block, context);
    }

    public MovingComputerBlock(@NotNull Block block, @NotNull PersistentDataContainer pdc) throws IOException {
        super(block, pdc);
    }

    public Optional<B> moveSafelyTo(Location location) {
        if (location.getWorld() == null) {
            return Optional.empty();
        }
        if (!location.getWorld().getBlockAt(location).getType().isAir()) {
            return Optional.empty();
        }
        return Optional.of(moveTo(location));
    }

    @Override
    protected void beforeStateRestore() {
        super.beforeStateRestore();
        //noinspection unchecked
        holder = new BlockHolder<>((B) this);
    }

    @Override
    protected void hotswap(B from) {
        super.hotswap(from);
        holder = from.holder;
        //noinspection unchecked
        holder.block = (B) this;
        from.holder = null;
    }

    @Override
    protected final ComputerManager.ComputerRequest<T> createNewComputer(
            @Nullable UUID ownerId, @Nullable PersistentDataContainer pdc) throws IOException {
        return ComputerManager.get().create(ownerId, () -> createNewComputer(holder), pdc);
    }

    protected abstract T createNewComputer(Supplier<B> blockSupplier);

    static final class BlockHolder<T extends PhysicalComputer<T, B>, B extends MovingComputerBlock<T, B>>
            implements Supplier<B> {

        volatile B block;

        BlockHolder(B block) {
            this.block = Preconditions.checkNotNull(block, "block");
        }

        @Override
        public B get() {
            return block;
        }

    }

}
