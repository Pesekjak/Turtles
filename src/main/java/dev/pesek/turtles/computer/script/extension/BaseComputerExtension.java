package dev.pesek.turtles.computer.script.extension;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.pesek.turtles.TurtlesKeys;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.computer.EnvVar;
import dev.pesek.turtles.computer.script.ComputerInvocationContext;
import dev.pesek.turtles.computer.script.Export;
import dev.pesek.turtles.computer.script.JactlUtils;
import dev.pesek.turtles.computer.script.ScriptExtension;
import dev.pesek.turtles.util.Docs;
import io.jactl.JactlContext;
import io.jactl.runtime.Continuation;
import io.jactl.runtime.RuntimeError;
import io.jactl.runtime.RuntimeState;
import org.bukkit.NamespacedKey;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Base extension that provides functions
 * for the yielding mechanism and computer environment access.
 */
@Docs("""
        Provides the base API for computer scripts.""")
public final class BaseComputerExtension implements ScriptExtension {

    private static final BaseComputerExtension INSTANCE = new BaseComputerExtension();

    public static BaseComputerExtension get() {
        return INSTANCE;
    }

    private BaseComputerExtension() {
    }

    @Export.Function(name = "yield", params = {})
    @Docs("""
            Yields the current thread to be used by other computers.
            You may be required to yield after a certain number of
            iterations is performed.""")
    public static Object yield(Continuation c, String source, int offset) {
        RuntimeState runtimeState = RuntimeState.getState();
        ComputerInvocationContext invocationContext = ScriptExtension.currentContext();
        JactlUtils.resetRuntimeStateLoopIterationCount(runtimeState);
        try {
            throw Continuation.suspendBlocking(source, offset, null, UnaryOperator.identity());
        } catch (Continuation caught) {
            invocationContext.captureState(caught);
            throw caught;
        }
    }

    @Export.Function(name = "getEnv", params = {"name"})
    @Docs("""
            Returns value of environment variable.""")
    public static Object getEnv(Continuation c, String source, int offset, String name) {
        throw ScriptExtension.delayAfter(source, offset, invocationContext -> {
            Computer computer = invocationContext.getComputer();
            return computer.getEnvironmentVariable(name)
                    .map(var -> {
                        Object value = var.get();
                        return switch (value) {
                            case Boolean _, Integer _, Long _, Double _, String _ -> value;
                            default -> value.toString();
                        };
                    })
                    .orElse(null);
        }, 100);
    }

    @Export.Function(name = "setEnv", params = {"name", "value"})
    @Docs("""
            Updates value of environment variable.
            Fails if the variable is read-only.""")
    public static Object setEnv(Continuation c, String source, int offset, String name, Object value) {
        throw ScriptExtension.delayAfter(source, offset, invocationContext -> {
            Computer computer = invocationContext.getComputer();
            //noinspection unchecked
            EnvVar<Object> var = (EnvVar<Object>) computer.getEnvironmentVariable(name).orElse(null);
            if (var == null) {
                throw new RuntimeError("Variable '" + name + "' does not exist", source, offset);
            }
            if (!var.isModifiable()) {
                throw new RuntimeError("Variable '" + name + "' is read-only", source, offset);
            }
            try {
                Object parsed = var.parse(value.toString());
                var.set(parsed);
                return null;
            } catch (CommandSyntaxException e) {
                throw new RuntimeError("Failed to parse variable value", source, offset, e);
            }
        }, 100);
    }

    @Export.Function(name = "envList", params = {})
    @Docs("""
            Returns list of all environment variables.""")
    public static List<String> envList() {
        Computer computer = ScriptExtension.currentContext().getComputer();
        return computer.getEnvironmentVariables().stream().map(EnvVar::getName).sorted().toList();
    }

    @Override
    public NamespacedKey key() {
        return TurtlesKeys.key("script_extension/base");
    }

    @Override
    public boolean exportFunctions(Computer computer) {
        return true;
    }

    @Override
    public void configureContext(Computer computer, JactlContext context) {
        context.getFunctions().deregisterFunction("nextLine");
    }

}
