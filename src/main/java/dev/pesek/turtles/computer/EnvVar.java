package dev.pesek.turtles.computer;

import com.google.common.base.Preconditions;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.pesek.turtles.TurtlesKeys;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public sealed interface EnvVar<T> permits StaticEnvVar, DynamicEnvVar {

    String getName();

    T get();

    void set(T value);

    boolean isModifiable();

    T parse(String s) throws CommandSyntaxException;

    void load(PersistentDataContainer pdc);

    void write(PersistentDataContainer pdc);

    static void validateName(String name) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkState(name.matches("[a-z0-9_]+"), "Environment variables names must "
                + "consist only of lowercase letters, digits and underscores");
    }

}

final class StaticEnvVar<T> implements EnvVar<T> {

    private final String name;
    private final T defaultValue;
    private final boolean modifiable;

    private volatile T value;
    private final Object lock = new Object();

    private final @Nullable ArgumentType<T> argumentType;
    private final PersistentDataType<?, T> persistentDataType;
    private final @Nullable Runnable updateHook;

    StaticEnvVar(String name, T defaultValue, boolean modifiable,
                 @Nullable ArgumentType<T> argumentType,
                 PersistentDataType<?, T> persistentDataType,
                 @Nullable Runnable updateHook) {
        EnvVar.validateName(name);
        this.name = name;
        this.defaultValue = Preconditions.checkNotNull(defaultValue, "defaultValue");
        this.value = defaultValue;
        this.modifiable = modifiable;

        if (modifiable) {
            Preconditions.checkNotNull(argumentType, "argumentType is required for modifiable variables");
        }

        this.argumentType = argumentType;
        this.persistentDataType = Preconditions.checkNotNull(persistentDataType, "persistentDataType");
        this.updateHook = updateHook;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public T get() {
        synchronized (lock) {
            return value;
        }
    }

    @Override
    public void set(T value) {
        synchronized (lock) {
            this.value = Preconditions.checkNotNull(value, "value");
        }

        if (updateHook != null) {
            updateHook.run();
        }
    }

    @Override
    public boolean isModifiable() {
        return modifiable;
    }

    @Override
    public T parse(String s) throws CommandSyntaxException {
        if (!modifiable || argumentType == null) {
            throw new UnsupportedOperationException("Environment variable '" + name + "' is read-only and cannot "
                    + "be parsed.");
        }
        return argumentType.parse(new StringReader(s));
    }

    @Override
    public void load(PersistentDataContainer pdc) {
        synchronized (lock) {
            value = pdc.get(key(), persistentDataType);
            if (value == null) {
                value = defaultValue;
            }
        }
        if (updateHook != null) {
            updateHook.run();
        }
    }

    @Override
    public void write(PersistentDataContainer pdc) {
        synchronized (lock) {
            pdc.set(key(), persistentDataType, value);
        }
    }

    private NamespacedKey key() {
        return TurtlesKeys.key("env/" + name);
    }

}

final class DynamicEnvVar<T> implements EnvVar<T> {

    private final String name;
    private final Supplier<T> supplier;

    DynamicEnvVar(String name, Supplier<T> supplier) {
        EnvVar.validateName(name);
        this.name = name;
        this.supplier = Preconditions.checkNotNull(supplier, "supplier");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public T get() {
        return Preconditions.checkNotNull(supplier.get(), "null env variables are unsupported");
    }

    @Override
    public void set(T value) {
        throw new UnsupportedOperationException("Dynamic environment variable '" + name + "' cannot be set manually.");
    }

    @Override
    public boolean isModifiable() {
        return false;
    }

    @Override
    public T parse(String s) {
        throw new UnsupportedOperationException("Dynamic environment variable '" + name + "' cannot be parsed.");
    }

    @Override
    public void load(PersistentDataContainer pdc) {
        // dynamic variables resolve dynamically and don't load state
    }

    @Override
    public void write(PersistentDataContainer pdc) {
        // dynamic variables resolve dynamically and don't save state
    }

}
