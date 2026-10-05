package dev.pesek.turtles.computer;

import com.google.common.base.Preconditions;
import dev.pesek.turtles.blocks.TurtleComputerBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.function.Supplier;

public final class TurtleComputer extends PhysicalComputer<TurtleComputer, TurtleComputerBlock> {

    public static final String DEFAULT_TURTLE_NAME = "Turtle";

    public TurtleComputer(Supplier<? extends TurtleComputerBlock> blockSupplier) {
        super(blockSupplier);
    }

    @Override
    protected void initEnvironment() {
        super.initEnvironment();
        setName(DEFAULT_TURTLE_NAME);
    }

    public boolean moveUp() {
        Preconditions.checkState(Bukkit.isPrimaryThread());
        TurtleComputerBlock turtleBlock = getBlock();
        Location location = turtleBlock.getBlock().getLocation();
        return turtleBlock.moveSafelyTo(location.add(new Vector(0, 1, 0))).isPresent();
    }

}
