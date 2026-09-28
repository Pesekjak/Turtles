package dev.pesek.turtles.util;

import com.google.common.base.Preconditions;
import dev.pesek.turtles.Turtles;
import org.jetbrains.annotations.Unmodifiable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class FreezingRegistry<K, V> {

    private final Map<K, V> map = new LinkedHashMap<>();

    protected FreezingRegistry() {
    }

    public final void register(V value) {
        Preconditions.checkNotNull(value, "value");
        K key = getKey(value);
        Preconditions.checkNotNull(key, "key");
        validRegister(key, value);
        synchronized (map) {
            checkAcceptsRegistrations();
            Preconditions.checkState(map.putIfAbsent(key, value) == null,
                    "Value + '" + key + "' is already registered");
        }
    }

    protected void validRegister(K key, V value) {
    }

    public @Unmodifiable List<V> getExtensions() {
        synchronized (map) {
            checkFinishedRegistrations();
            return List.copyOf(map.values());
        }
    }

    public boolean acceptsRegistrations() {
        return !Turtles.getInstance().finishedLoading();
    }

    public void checkAcceptsRegistrations() {
        Preconditions.checkState(acceptsRegistrations(), "Registration has already closed");
    }

    public void checkFinishedRegistrations() {
        Preconditions.checkState(!acceptsRegistrations(), "Registration is still ongoing");
    }

    protected abstract K getKey(V value);

}
