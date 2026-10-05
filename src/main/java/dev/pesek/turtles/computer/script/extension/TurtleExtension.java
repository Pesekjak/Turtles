package dev.pesek.turtles.computer.script.extension;

import dev.pesek.turtles.TurtlesKeys;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.computer.TurtleComputer;
import dev.pesek.turtles.computer.script.Export;
import dev.pesek.turtles.computer.script.ScriptExtension;
import dev.pesek.turtles.util.Docs;
import io.jactl.runtime.Continuation;
import org.bukkit.NamespacedKey;

@Docs("""
        Provides the base API for turtles.""")
public final class TurtleExtension implements ScriptExtension {

    private static final TurtleExtension INSTANCE = new TurtleExtension();

    public static TurtleExtension get() {
        return INSTANCE;
    }

    private TurtleExtension() {
    }

    @Export.Function(name = "moveUp", params = {})
    public static Object moveUp(Continuation c, String source, int offset) {
        throw ScriptExtension.onMainThread(source, offset, ctx -> {
            TurtleComputer turtle = (TurtleComputer) ctx.getComputer();
            return turtle.moveUp();
        }, 500);
    }

    @Override
    public NamespacedKey key() {
        return TurtlesKeys.key("script_extension/turtle");
    }

    @Override
    public boolean exportFunctions(Computer computer) {
        return computer instanceof TurtleComputer;
    }

}
