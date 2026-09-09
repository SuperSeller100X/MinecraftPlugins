package dev.superseller.playerbank.storage;

import dev.superseller.playerbank.PlayerBankPlugin;
import dev.superseller.playerbank.config.BankConfig;
import dev.superseller.playerbank.model.BankAccount;
import dev.superseller.playerbank.model.BankLogEntry;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class BankStorage {

    private final PlayerBankPlugin plugin;
    private final BankConfig config;
    private final Map<UUID, BankAccount> accounts = new ConcurrentHashMap<>();
    private final Map<UUID, String> menuPreferences = new ConcurrentHashMap<>();
    private File file;
    private ScheduledTask autosave;
    private volatile boolean dirty;

    public BankStorage(PlayerBankPlugin plugin, BankConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.file = new File(plugin.getDataFolder(), config.storageFile());
    }

    public void reloadPath() {
        this.file = new File(plugin.getDataFolder(), config.storageFile());
        startAutosave();
    }

    public void load() {
        accounts.clear();
        if (!file.exists()) {
            startAutosave();
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("accounts");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    ConfigurationSection sec = root.getConfigurationSection(key);
                    if (sec == null) {
                        continue;
                    }
                    BankAccount acc = new BankAccount(uuid, sec.getString("name", "unknown"));
                    acc.balance(sec.getDouble("balance", 0));
                    for (Map<?, ?> map : sec.getMapList("logs")) {
                        long time = toLong(map.get("time"));
                        // Map<?,?>.getOrDefault(key, "literal") does not compile:
                        // the default must match the captured value type.
                        Object rawType = map.get("type");
                        String type = rawType == null ? "?" : String.valueOf(rawType);
                        double amount = toDouble(map.get("amount"));
                        Object rawNote = map.get("note");
                        String note = rawNote == null ? "" : String.valueOf(rawNote);
                        acc.appendLoadedLog(new BankLogEntry(time, type, amount, note));
                    }
                    accounts.put(uuid, acc);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Skipping invalid account key: " + key);
                }
            }
        }
        ConfigurationSection prefs = yaml.getConfigurationSection("menu-preferences");
        if (prefs != null) {
            for (String key : prefs.getKeys(false)) {
                try {
                    menuPreferences.put(UUID.fromString(key), prefs.getString(key, ""));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Skipping invalid menu preference key: " + key);
                }
            }
        }
        startAutosave();
        plugin.getLogger().info("Loaded " + accounts.size() + " bank accounts.");
    }

    /**
     * Saves all accounts. Thread-safe: takes a tiny synchronized snapshot of
     * each account first, so it can run from the async scheduler (autosave),
     * the main/global thread (disable, reload) or an admin command without
     * blocking region threads.
     */
    public void save() {
        List<AccountSnapshot> snapshots = snapshotAccounts();
        Map<UUID, String> prefs = new LinkedHashMap<>(menuPreferences);
        YamlConfiguration yaml = new YamlConfiguration();
        for (AccountSnapshot acc : snapshots) {
            String path = "accounts." + acc.uuid();
            yaml.set(path + ".name", acc.name());
            yaml.set(path + ".balance", acc.balance());
            List<Map<String, Object>> logs = new ArrayList<>();
            for (BankLogEntry e : acc.logs()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("time", e.time());
                m.put("type", e.type());
                m.put("amount", e.amount());
                m.put("note", e.note());
                logs.add(m);
            }
            yaml.set(path + ".logs", logs);
        }
        for (Map.Entry<UUID, String> entry : prefs.entrySet()) {
            yaml.set("menu-preferences." + entry.getKey(), entry.getValue());
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save bank data: " + e.getMessage());
        }
    }

    /** Immutable, consistent view of one account, taken under its lock. */
    public record AccountSnapshot(UUID uuid, String name, double balance, List<BankLogEntry> logs) {
    }

    private List<AccountSnapshot> snapshotAccounts() {
        List<AccountSnapshot> out = new ArrayList<>(accounts.size());
        for (BankAccount acc : accounts.values()) {
            synchronized (acc) {
                out.add(new AccountSnapshot(acc.uuid(), acc.lastName(), acc.balance(), acc.logSnapshot()));
            }
        }
        return out;
    }

    public BankAccount getOrCreate(UUID uuid, String name) {
        return accounts.compute(uuid, (id, existing) -> {
            if (existing == null) {
                existing = new BankAccount(id, name);
                dirty = true;
            } else if (name != null) {
                existing.lastName(name);
            }
            return existing;
        });
    }

    public BankAccount get(UUID uuid) {
        return accounts.get(uuid);
    }

    public Collection<BankAccount> all() {
        return accounts.values();
    }

    public void markDirty() {
        dirty = true;
    }

    /** Personal menu style ("chest"/"dialog"), or null when unset. */
    public String menuPreference(UUID uuid) {
        return menuPreferences.get(uuid);
    }

    public void setMenuPreference(UUID uuid, String style) {
        menuPreferences.put(uuid, style);
        dirty = true;
    }

    public void log(BankAccount acc, String type, double amount, String note) {
        acc.addLog(new BankLogEntry(System.currentTimeMillis(), type, amount, note), config.maxLogEntries());
        dirty = true;
    }

    private void startAutosave() {
        if (autosave != null) {
            autosave.cancel();
            autosave = null;
        }
        long seconds = config.autosaveSeconds();
        if (seconds <= 0) {
            return;
        }
        long ticks = seconds * 20L;
        // Folia-safe: the repeating check runs on the global region thread and
        // only reads a volatile flag; the disk write itself is async.
        autosave = plugin.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(plugin, timer -> {
                    if (dirty) {
                        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> save());
                    }
                }, ticks, ticks);
    }

    private static long toLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0d;
        }
    }
}
