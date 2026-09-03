package dev.superseller.swifttpa.storage;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Flat-file storage: one small YAML file per player in the
 * {@code playerdata/} folder. No external dependencies; works identically on
 * Linux, Windows and macOS. The in-memory cache is the source of truth;
 * writes go through to disk on every save call.
 */
public final class YamlStorage implements PlayerDataStorage {

    private final File folder;
    private final Logger logger;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Object ioLock = new Object();

    public YamlStorage(File folder, Logger logger) {
        this.folder = folder;
        this.logger = logger;
    }

    @Override
    public void init() {
        if (!folder.exists() && !folder.mkdirs()) {
            logger.warning("Could not create player data folder " + folder);
        }
    }

    @Override
    public PlayerData data(UUID uuid) {
        return cache.computeIfAbsent(uuid, PlayerData::new);
    }

    private File fileFor(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }

    @Override
    public void loadPlayer(UUID uuid) {
        synchronized (ioLock) {
            File file = fileFor(uuid);
            PlayerData data = data(uuid);
            if (!file.exists()) {
                return;
            }
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                data.setRequestsEnabled(yaml.getBoolean("enabled", true));
                data.loadBlocked(yaml.getStringList("blocked"));
                ConfigurationSection stats = yaml.getConfigurationSection("stats");
                data.setStats(
                        stats == null ? 0L : stats.getLong("sent", 0L),
                        stats == null ? 0L : stats.getLong("accepted", 0L),
                        stats == null ? 0L : stats.getLong("denied", 0L),
                        stats == null ? 0L : stats.getLong("teleported", 0L));
            } catch (Throwable e) {
                logger.warning("Could not read " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void savePlayer(UUID uuid) {
        synchronized (ioLock) {
            PlayerData data = cache.get(uuid);
            if (data == null) {
                return;
            }
            if (!folder.exists() && !folder.mkdirs()) {
                logger.warning("Could not create player data folder " + folder);
                return;
            }
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.set("enabled", data.requestsEnabled());
                yaml.set("blocked", data.serializeBlocked());
                yaml.set("stats.sent", data.sentCount());
                yaml.set("stats.accepted", data.acceptedCount());
                yaml.set("stats.denied", data.deniedCount());
                yaml.set("stats.teleported", data.teleportedCount());
                yaml.save(fileFor(uuid));
            } catch (IOException e) {
                logger.warning("Could not save data for " + uuid + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void saveAll() {
        synchronized (ioLock) {
            for (UUID uuid : cache.keySet()) {
                PlayerData data = cache.get(uuid);
                if (data == null) {
                    continue;
                }
                if (!folder.exists() && !folder.mkdirs()) {
                    logger.warning("Could not create player data folder " + folder);
                    return;
                }
                try {
                    YamlConfiguration yaml = new YamlConfiguration();
                    yaml.set("enabled", data.requestsEnabled());
                    yaml.set("blocked", data.serializeBlocked());
                    yaml.set("stats.sent", data.sentCount());
                    yaml.set("stats.accepted", data.acceptedCount());
                    yaml.set("stats.denied", data.deniedCount());
                    yaml.set("stats.teleported", data.teleportedCount());
                    yaml.save(fileFor(uuid));
                } catch (IOException e) {
                    logger.warning("Could not save data for " + uuid + ": " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void close() {
        // No resources to release for flat files.
    }
}
