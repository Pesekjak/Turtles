package dev.pesek.turtles.blocks;

import dev.pesek.turtles.TurtlesItems;
import dev.pesek.turtles.computer.TurtleComputer;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.function.Supplier;

public final class TurtleComputerBlock extends MovingComputerBlock<TurtleComputer, TurtleComputerBlock> {

    public TurtleComputerBlock(@NotNull Block block, @NotNull BlockCreateContext context) throws IOException {
        super(block, context);
    }

    public TurtleComputerBlock(@NotNull Block block, @NotNull PersistentDataContainer pdc) throws IOException {
        super(block, pdc);
    }

    @Override
    protected TurtleComputer createNewComputer(Supplier<TurtleComputerBlock> blockSupplier) {
        return new TurtleComputer(blockSupplier);
    }

    @Override
    protected ItemStack getAsNewDrop() {
        return TurtlesItems.TURTLE;
    }

}
