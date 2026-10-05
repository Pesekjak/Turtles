package dev.pesek.turtles;

import dev.pesek.turtles.blocks.BasicComputerBlock;
import dev.pesek.turtles.blocks.TurtleComputerBlock;
import io.github.pylonmc.rebar.block.RebarBlock;
import org.bukkit.Material;

public interface TurtlesBlocks {

    static void init() {
        RebarBlock.register(TurtlesKeys.Items.COMPUTER, Material.LOOM, BasicComputerBlock.class);
        RebarBlock.register(TurtlesKeys.Items.TURTLE, Material.DRIED_GHAST, TurtleComputerBlock.class);
    }

}
