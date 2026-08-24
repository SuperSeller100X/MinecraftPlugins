package dev.superseller.shardtools.economy;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shard balances stored in data/shards.yml (uuid -> name + balance).
 * All mutations are atomic so the award task and commands can safely run
 * on any Folia region thread.
 */
public final class ShardAccounts {

    /** Player account: last known name plus shard balance. */
    public static final class Account {

        public final UUID uuid;
        public volatile String name;
        public volatile long balance;

        Account(UUID uuid, String name, long balance) {
            this.uuid = uuid;
            this.name = name;
            this.balance = balance;
        }
    }

    private final JavaPlugin plugin;
    private final Map<UUID, Account> accounts = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public ShardAccounts(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = file();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = data.getConfigurationSection("accounts");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String name = root.getString(key + ".name", "unknown");
                long balance = root.getLong(key + ".balance", 0L);
                accounts.put(uuid, new Account(uuid, name, Math.max(0L, balance)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping invalid uuid in shards.yml: " + key);
            }
        }
        plugin.getLogger().info("Loaded " + accounts.size() + " shard balances");
    }

    public Account account(UUID uuid, String name, long defaultBalance) {
        return accounts.compute(uuid, (id, existing) -> {
            if (existing == null) {
                return new Account(id, name != null ? name : "unknown", defaultBalance);
            }
            if (name != null && !name.equals(existing.name)) {
                existing.name = name;
            }
            return existing;
        });
    }

    public long balance(UUID uuid, String name) {
        return account(uuid, name, startBalance()).balance;
    }

    public long set(UUID uuid, String name, long value) {
        Account account = account(uuid, name, startBalance());
        synchronized (account) {
            account.balance = Math.max(0L, value);
        }
        dirty = true;
        return account.balance;
    }

    public long add(UUID uuid, String name, long delta) {
        Account account = account(uuid, name, startBalance());
        synchronized (account) {
            account.balance = Math.max(0L, account.balance + delta);
        }
        dirty = true;
        return account.balance;
    }

    /** Withdraws; returns null when the balance is insufficient. */
    public Long take(UUID uuid, String name, long amount) {
        if (amount < 0) {
            return null;
        }
        Account account = account(uuid, name, startBalance());
        synchronized (account) {
            if (account.balance < amount) {
                return null;
            }
            account.balance -= amount;
            dirty = true;
            return account.balance;
        }
    }

    public List<Account> top(int limit) {
        List<Account> sorted = new ArrayList<>(accounts.values());
        sorted.sort(Comparator.comparingLong((Account account) -> account.balance).reversed());
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    private long startBalance() {
        return plugin.getConfig().getLong("currency.start-balance", 0L);
    }

    public boolean isDirty() {
        return dirty;
    }

    public void saveAsync() {
        dirty = false;
        dev.superseller.shardtools.scheduler.PlatformScheduler.runAsync(this::saveNow);
    }

    public synchronized void saveNow() {
        dirty = false;
        YamlConfiguration data = new YamlConfiguration();
        for (Account account : accounts.values()) {
            data.set("accounts." + account.uuid + ".name", account.name);
            data.set("accounts." + account.uuid + ".balance", account.balance);
        }
        try {
            File folder = plugin.getDataFolder();
            if (!folder.isDirectory() && !folder.mkdirs()) {
                plugin.getLogger().warning("Could not create plugin data folder");
                return;
            }
            data.save(file());
        } catch (IOException error) {
            plugin.getLogger().log(Level.WARNING, "Could not save shards.yml", error);
        }
    }

    private File file() {
        return new File(plugin.getDataFolder(), "shards.yml");
    }
}
