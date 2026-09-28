package dev.pesek.turtles.computer.script;

import io.jactl.runtime.Continuation;
import io.jactl.runtime.RuntimeState;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class JactlUtils {

    private static final VarHandle VH_RUNTIME_STATE_LOOP_ITERATION_COUNT;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(RuntimeState.class, MethodHandles.lookup());
            VH_RUNTIME_STATE_LOOP_ITERATION_COUNT = lookup.findVarHandle(RuntimeState.class,
                    "loopIterationCount", long.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void resetRuntimeStateLoopIterationCount(RuntimeState runtimeState) {
        VH_RUNTIME_STATE_LOOP_ITERATION_COUNT.set((RuntimeState) runtimeState, (long) 0L);
    }

    private static final VarHandle VH_CONTINUATION_PARENT;
    private static final VarHandle VH_CONTINUATION_CHILD;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(Continuation.class, MethodHandles.lookup());
            VH_CONTINUATION_PARENT = lookup.findVarHandle(Continuation.class, "parent", Continuation.class);
            VH_CONTINUATION_CHILD = lookup.findVarHandle(Continuation.class, "child", Continuation.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static @Unmodifiable Set<Continuation> collectContinuationTree(Continuation node) {
        Set<Continuation> collected = new HashSet<>();
        collected.add(node);
        Continuation parent = (Continuation) VH_CONTINUATION_PARENT.get((Continuation) node);
        Continuation child = (Continuation) VH_CONTINUATION_CHILD.get((Continuation) node);
        while (parent != null && collected.add(parent)) {
            parent = (Continuation) VH_CONTINUATION_PARENT.get((Continuation) parent);
        }
        while (child != null && collected.add(child)) {
            child = (Continuation) VH_CONTINUATION_CHILD.get((Continuation) child);
        }
        return Collections.unmodifiableSet(collected);
    }

    private JactlUtils() {
        throw new UnsupportedOperationException();
    }

}
