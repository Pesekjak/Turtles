package dev.pesek.turtles;

import dev.pesek.turtles.blocks.BasicComputerBlock;
import io.github.pylonmc.rebar.block.RebarBlock;
import org.bukkit.Material;

public interface TurtlesBlocks {

    static void init() {
        RebarBlock.register(TurtlesKeys.Items.COMPUTER, Material.LOOM, BasicComputerBlock.class);
    }

}
