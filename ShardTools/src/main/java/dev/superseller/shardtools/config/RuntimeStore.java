package dev.superseller.shardtools.config;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.superseller.shardtools.scheduler.PlatformScheduler;

/**
 * Runtime overrides edited through /st setprice, /st interval, /st amount and
 * /st award. Persisted in data/runtime.yml so they survive restarts and
 * config.yml edits.
 */
public final class RuntimeStore {

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration data = new YamlConfiguration();
    private volatile boolean dirty;

    public RuntimeStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "runtime.yml");
        load();
    }

    private void load() {
        if (file.isFile()) {
            try {
                data.load(file);
            } catch (Exception error) {
                plugin.getLogger().log(Level.WARNING, "Could not read runtime.yml, starting clean", error);
            }
        }
    }

    public void reload() {
        load();
    }

    public Long priceOverride(String itemId) {
        return data.getLong("prices." + itemId, -1L) < 0 ? null : data.getLong("prices." + itemId);
    }

    public Map<String, Long> priceOverrides() {
        Map<String, Long> out = new HashMap<>();
        ConfigurationSection section = data.getConfigurationSection("prices");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                long value = section.getLong(key, -1L);
                if (value >= 0) {
                    out.put(key, value);
                }
            }
        }
        return out;
    }

    public void setPriceOverride(String itemId, long price) {
        data.set("prices." + itemId, price);
        saveLater();
    }

    public Long awardIntervalOverride() {
        return data.contains("award.interval-minutes") ? data.getLong("award.interval-minutes") : null;
    }

    public Long awardAmountOverride() {
        return data.contains("award.amount") ? data.getLong("award.amount") : null;
    }

    public Boolean awardEnabledOverride() {
        return data.contains("award.enabled") ? data.getBoolean("award.enabled") : null;
    }

    public void setAwardInterval(long minutes) {
        data.set("award.interval-minutes", Math.max(1L, minutes));
        saveLater();
    }

    public void setAwardAmount(long amount) {
        data.set("award.amount", Math.max(0L, amount));
        saveLater();
    }

    public void setAwardEnabled(boolean enabled) {
        data.set("award.enabled", enabled);
        saveLater();
    }

    /** Debounced asynchronous save (Folia-safe). */
    public void saveLater() {
        dirty = true;
        PlatformScheduler.runAsync(this::saveNow);
    }

    public synchronized void saveNow() {
        dirty = false;
        try {
            File folder = plugin.getDataFolder();
            if (!folder.isDirectory() && !folder.mkdirs()) {
                plugin.getLogger().warning("Could not create plugin data folder");
                return;
            }
            data.save(file);
        } catch (IOException error) {
            plugin.getLogger().log(Level.WARNING, "Could not save runtime.yml", error);
        }
    }

    public boolean isDirty() {
        return dirty;
    }
}
