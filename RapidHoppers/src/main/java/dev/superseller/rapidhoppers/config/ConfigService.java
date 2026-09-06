package dev.superseller.rapidhoppers.config;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Owns loading, live mutation and persistence of {@code config.yml}.
 *
 * <p>All writes go through this class so that runtime changes made by
 * {@code /rha ...} or the GUI survive a restart. Files are written with the
 * Bukkit YAML API, which handles platform line endings, so the plugin behaves
 * identically on Linux, Windows and macOS.</p>
 */
public final class ConfigService {

    private final JavaPlugin plugin;
    private final Settings settings = new Settings();

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Settings settings() {
        return settings;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        settings.load(plugin.getConfig());
    }

    /** Writes the current in-memory settings back to disk. */
    public void save() {
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("enabled", settings.isEnabled());
        cfg.set("engine.interval-ticks", settings.getIntervalTicks());
        for (Settings.ContainerType type : Settings.ContainerType.values()) {
            cfg.set(type.configKey(), settings.isContainerEnabled(type));
        }
        cfg.set("worlds.mode", settings.getWorldMode().name());
        cfg.set("worlds.list", new ArrayList<>(settings.getWorldList()));
        cfg.set("performance.throttle.enabled", settings.isThrottleEnabled());
        cfg.set("performance.throttle.soft-tps", settings.getThrottleSoftTps());
        cfg.set("performance.throttle.hard-tps", settings.getThrottleHardTps());
        cfg.set("performance.max-containers-per-chunk", settings.getMaxContainersPerChunk());
        cfg.set("debug", settings.isDebug());
        plugin.saveConfig();
    }

    /** Toggles a world in the configured world list; returns true if now listed. */
    public boolean toggleWorld(String worldName) {
        List<String> list = settings.getWorldList();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equalsIgnoreCase(worldName)) {
                list.remove(i);
                save();
                return false;
            }
        }
        list.add(worldName);
        save();
        return true;
    }
}
