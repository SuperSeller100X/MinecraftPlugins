package dev.superseller.chestlock.storage;

import dev.superseller.chestlock.ChestLockPlugin;
import dev.superseller.chestlock.config.PluginConfig;
import dev.superseller.chestlock.model.PlayerSettings;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Cross-platform YAML persistence for each player's non-secret convenience settings. */
public final class PlayerSettingsStore {
    private final ChestLockPlugin plugin;
    private final Map<UUID, PlayerSettings> settings = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean saving = new AtomicBoolean();
    private final Object fileLock = new Object();
    private final File file;

    public PlayerSettingsStore(ChestLockPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    public void load() {
        settings.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        PluginConfig config = plugin.runtimeConfig();
        for (String rawId : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(rawId);
                String path = "players." + rawId + '.';
                settings.put(id, new PlayerSettings(
                        clamp(yaml.getInt(path + "default-duration-seconds", config.defaultDurationSeconds()),
                                config.minDurationSeconds(), config.maxDurationSeconds()),
                        yaml.getBoolean(path + "default-create-key", config.defaultCreateKey()),
                        yaml.getBoolean(path + "auto-open", config.defaultAutoOpen()),
                        yaml.getBoolean(path + "sounds", config.defaultSounds()),
                        yaml.getBoolean(path + "particles", config.defaultParticles()),
                        yaml.getBoolean(path + "action-bar", config.defaultActionBar()),
                        yaml.getBoolean(path + "break-confirmation", config.defaultBreakConfirmation()),
                        yaml.getBoolean(path + "attempt-notifications", config.defaultAttemptNotifications())
                ));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring invalid player UUID in players.yml: " + rawId);
            }
        }
    }

    public PlayerSettings get(UUID playerId) {
        return settings.computeIfAbsent(playerId, ignored -> defaults());
    }

    public void put(UUID playerId, PlayerSettings value) {
        settings.put(playerId, value);
        dirty.set(true);
        saveAsync();
    }

    public void saveAsync() {
        if (!saving.compareAndSet(false, true)) {
            return;
        }
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            boolean saveFailed = false;
            try {
                while (dirty.getAndSet(false)) {
                    if (!saveSnapshot()) {
                        saveFailed = true;
                        break;
                    }
                }
            } finally {
                saving.set(false);
                if (dirty.get() && !saveFailed) {
                    saveAsync();
                }
            }
        });
    }

    public void saveNow() {
        dirty.set(false);
        saveSnapshot();
    }

    private PlayerSettings defaults() {
        PluginConfig config = plugin.runtimeConfig();
        return new PlayerSettings(
                config.defaultDurationSeconds(), config.defaultCreateKey(), config.defaultAutoOpen(),
                config.defaultSounds(), config.defaultParticles(), config.defaultActionBar(),
                config.defaultBreakConfirmation(), config.defaultAttemptNotifications());
    }

    private boolean saveSnapshot() {
        Map<UUID, PlayerSettings> snapshot = Map.copyOf(settings);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);
        snapshot.forEach((id, value) -> {
            String path = "players." + id + '.';
            yaml.set(path + "default-duration-seconds", value.defaultDurationSeconds());
            yaml.set(path + "default-create-key", value.defaultCreateKey());
            yaml.set(path + "auto-open", value.autoOpen());
            yaml.set(path + "sounds", value.sounds());
            yaml.set(path + "particles", value.particles());
            yaml.set(path + "action-bar", value.actionBar());
            yaml.set(path + "break-confirmation", value.breakConfirmation());
            yaml.set(path + "attempt-notifications", value.attemptNotifications());
        });

        synchronized (fileLock) {
            try {
                Files.createDirectories(file.toPath().getParent());
                File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
                yaml.save(temporary);
                try {
                    Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                return true;
            } catch (IOException exception) {
                dirty.set(true);
                plugin.getLogger().severe("Could not save players.yml: " + exception.getMessage());
                return false;
            }
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
