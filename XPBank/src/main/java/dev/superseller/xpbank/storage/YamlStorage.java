package dev.superseller.xpbank.storage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Flat-file storage in {@code balances.yml}. No external dependencies; works
 * identically on Linux, Windows and macOS. In-memory maps are the source of
 * truth and are written through on every mutation and on autosave.
 */
public final class YamlStorage implements BankStorage {

    private final File file;
    private final Logger logger;
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private final Object ioLock = new Object();

    public YamlStorage(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    @Override
    public void load() {
        balances.clear();
        names.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection accounts = yaml.getConfigurationSection("accounts");
        if (accounts == null) {
            return;
        }
        for (String key : accounts.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long amount = accounts.getLong(key + ".balance", 0L);
                String name = accounts.getString(key + ".name", null);
                if (amount > 0 || name != null) {
                    balances.put(uuid, Math.max(0L, amount));
                    if (name != null) {
                        names.put(uuid, name);
                    }
                }
            } catch (IllegalArgumentException e) {
                logger.warning("Skipping invalid account entry '" + key + "' in balances.yml");
            }
        }
    }

    @Override
    public void save() {
        synchronized (ioLock) {
            YamlConfiguration yaml = new YamlConfiguration();
            Map<UUID, Long> snapshot = new HashMap<>(balances);
            for (Map.Entry<UUID, Long> e : snapshot.entrySet()) {
                String base = "accounts." + e.getKey();
                yaml.set(base + ".balance", e.getValue());
                String name = names.get(e.getKey());
                if (name != null) {
                    yaml.set(base + ".name", name);
                }
            }
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    logger.warning("Could not create data folder " + parent);
                }
                yaml.save(file);
            } catch (IOException e) {
                logger.severe("Failed to save balances.yml: " + e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        save();
    }

    @Override
    public long get(UUID uuid) {
        return balances.getOrDefault(uuid, 0L);
    }

    @Override
    public void set(UUID uuid, String name, long amount) {
        balances.put(uuid, Math.max(0L, amount));
        if (name != null) {
            names.put(uuid, name);
        }
    }

    @Override
    public void rememberName(UUID uuid, String name) {
        if (name != null) {
            names.put(uuid, name);
        }
    }

    @Override
    public String nameOf(UUID uuid) {
        return names.get(uuid);
    }

    @Override
    public Map<UUID, Long> all() {
        return new HashMap<>(balances);
    }

    @Override
    public List<Entry> top(int limit) {
        List<Entry> list = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : balances.entrySet()) {
            list.add(new Entry(e.getKey(), names.getOrDefault(e.getKey(), null), e.getValue()));
        }
        list.sort(Comparator.comparingLong(Entry::amount).reversed());
        if (list.size() > limit) {
            return new ArrayList<>(list.subList(0, limit));
        }
        return list;
    }

    @Override
    public long totalStored() {
        long sum = 0;
        for (long v : balances.values()) {
            sum += v;
        }
        return sum;
    }
}
