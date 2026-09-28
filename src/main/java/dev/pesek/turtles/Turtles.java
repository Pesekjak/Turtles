package dev.pesek.turtles;

import com.google.common.base.Preconditions;
import de.exlll.configlib.YamlConfigurations;
import dev.pesek.turtles.computer.script.ComputerEnv;
import dev.pesek.turtles.computer.script.JactlExport;
import dev.pesek.turtles.computer.script.ScriptExtension;
import dev.pesek.turtles.computer.script.extension.BaseComputerExtension;
import io.github.pylonmc.rebar.addon.RebarAddon;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class Turtles extends JavaPlugin implements RebarAddon, Listener {

    private static Turtles instance;

    public static Turtles getInstance() {
        return Preconditions.checkNotNull(instance, "instance");
    }

    public static Logger logger() {
        return getInstance().getSLF4JLogger();
    }

    public static TurtlesConfig config() {
        return getInstance().config;
    }

    public static String version() {
        return getInstance().getPluginMeta().getVersion();
    }

    private TurtlesConfig config;
    private final AtomicBoolean finishedLoading = new AtomicBoolean(false);

    public boolean finishedLoading() {
        return finishedLoading.get();
    }

    @Override
    public void onEnable() {
        instance = this;
        config = YamlConfigurations.update(getDataPath().resolve("config.yml"), TurtlesConfig.class);

        registerWithRebar();

        TurtlesPages.init();
        TurtlesItems.init();
        TurtlesBlocks.init();

        ScriptExtension.register(BaseComputerExtension.get());

        System.setProperty("jactl.loop.timeout-freq-check", String.valueOf(config.loopTimeoutFreqCheck()));

        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        ComputerEnv.get().shutdown();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onServerLoad(ServerLoadEvent event) {
        finishedLoading.set(true);
        JactlExport.exportTypes();
    }

    @Override
    public @NotNull JavaPlugin getJavaPlugin() {
        return this;
    }

    @Override
    public @NotNull Material getMaterial() {
        return Material.CRAFTER;
    }

    @Override
    public @NotNull Locale getDefaultLanguage() {
        return Locale.ENGLISH;
    }

}
