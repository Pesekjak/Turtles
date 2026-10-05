package dev.pesek.turtles.blocks;

import dev.pesek.turtles.TurtlesItems;
import dev.pesek.turtles.computer.BasicComputer;
import dev.pesek.turtles.computer.ComputerManager;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.UUID;

public final class BasicComputerBlock extends ComputerBlock<BasicComputer, BasicComputerBlock> {

    public BasicComputerBlock(@NotNull Block block, @NotNull BlockCreateContext context) throws IOException {
        super(block, context);
    }

    public BasicComputerBlock(@NotNull Block block, @NotNull PersistentDataContainer pdc) throws IOException {
        super(block, pdc);
    }

    @Override
    protected ComputerManager.ComputerRequest<BasicComputer> createNewComputer(
            @Nullable UUID ownerId, @Nullable PersistentDataContainer pdc) throws IOException {
        return ComputerManager.get().create(ownerId, () -> new BasicComputer(this), pdc);
    }

    @Override
    protected ItemStack getAsNewDrop() {
        return TurtlesItems.COMPUTER;
    }

}
