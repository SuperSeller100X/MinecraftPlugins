package dev.superseller.justgambling.storage;

import dev.superseller.justgambling.config.PluginSettings;
import dev.superseller.justgambling.model.GameType;
import dev.superseller.justgambling.model.PlayerStats;
import dev.superseller.justgambling.model.Transaction;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Portable YAML-backed persistence. Runtime mutations are kept in memory and
 * autosaved on a daemon writer so wager resolution never waits on disk I/O.
 */
public final class GamblingStore {
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final File dataFile;
    private final ConcurrentHashMap<UUID, Account> accounts = new ConcurrentHashMap<>();
    private final Object saveLock = new Object();
    private final ExecutorService writer;
    private volatile double jackpotPool;
    private volatile boolean closed;

    public GamblingStore(JavaPlugin plugin, PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        String configuredFile = plugin.getConfig().getString("storage.data-file", "data.yml");
        if (configuredFile == null || configuredFile.isBlank() || configuredFile.contains("..")) {
            configuredFile = "data.yml";
        }
        this.dataFile = new File(plugin.getDataFolder(), configuredFile);
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "JustGambling-data-writer");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void load() {
        if (!dataFile.exists()) {
            jackpotPool = settings.jackpotSeed();
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        jackpotPool = finiteNonNegative(data.getDouble("jackpot-pool", settings.jackpotSeed()), settings.jackpotSeed());
        ConfigurationSection section = data.getConfigurationSection("accounts");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection accountSection = section.getConfigurationSection(key);
                if (accountSection == null) {
                    continue;
                }
                Account account = new Account(
                        accountSection.getString("last-known-name", "Unknown"),
                        finiteNonNegative(accountSection.getDouble("fallback-balance", settings.fallbackStartingBalance()), 0.0));
                account.games = Math.max(0L, accountSection.getLong("stats.games", 0L));
                account.wins = Math.max(0L, accountSection.getLong("stats.wins", 0L));
                account.losses = Math.max(0L, accountSection.getLong("stats.losses", 0L));
                account.wagered = finiteNonNegative(accountSection.getDouble("stats.wagered", 0.0), 0.0);
                account.paidOut = finiteNonNegative(accountSection.getDouble("stats.paid-out", 0.0), 0.0);
                account.bestWin = finiteNonNegative(accountSection.getDouble("stats.best-win", 0.0), 0.0);

                List<Map<?, ?>> records = accountSection.getMapList("history");
                for (Map<?, ?> record : records) {
                    Transaction transaction = readTransaction(uuid, record);
                    if (transaction != null) {
                        account.history.addLast(transaction);
                    }
                }
                trim(account.history);
                accounts.put(uuid, account);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid JustGambling account key: " + key);
            }
        }
    }

    public double fallbackBalance(UUID uuid, String lastKnownName) {
        Account account = account(uuid, lastKnownName);
        synchronized (account) {
            return account.fallbackBalance;
        }
    }

    public boolean debitFallback(UUID uuid, String lastKnownName, double amount) {
        if (!finitePositive(amount)) {
            return false;
        }
        Account account = account(uuid, lastKnownName);
        synchronized (account) {
            if (account.fallbackBalance + 1.0e-9 < amount) {
                return false;
            }
            account.fallbackBalance -= amount;
            if (account.fallbackBalance < 0.0) {
                account.fallbackBalance = 0.0;
            }
            return true;
        }
    }

    public boolean creditFallback(UUID uuid, String lastKnownName, double amount) {
        if (!finitePositive(amount)) {
            return false;
        }
        Account account = account(uuid, lastKnownName);
        synchronized (account) {
            double updated = account.fallbackBalance + amount;
            if (!Double.isFinite(updated)) {
                return false;
            }
            account.fallbackBalance = updated;
            return true;
        }
    }

    public double setFallback(UUID uuid, String lastKnownName, double amount) {
        Account account = account(uuid, lastKnownName);
        synchronized (account) {
            account.fallbackBalance = finiteNonNegative(amount, 0.0);
            return account.fallbackBalance;
        }
    }

    public void rememberName(UUID uuid, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        Account account = account(uuid, name);
        synchronized (account) {
            account.lastKnownName = name;
        }
    }

    public PlayerStats stats(UUID uuid, String name) {
        Account account = account(uuid, name);
        synchronized (account) {
            return new PlayerStats(account.games, account.wins, account.losses, account.wagered,
                    account.paidOut, account.bestWin);
        }
    }

    public List<Transaction> history(UUID uuid, String name, int limit) {
        Account account = account(uuid, name);
        synchronized (account) {
            return account.history.stream().limit(Math.max(1, limit)).toList();
        }
    }

    public List<Transaction> allHistory(int limit) {
        List<Transaction> result = new ArrayList<>();
        for (Account account : accounts.values()) {
            synchronized (account) {
                result.addAll(account.history);
            }
        }
        result.sort(Comparator.comparing(Transaction::timestamp).reversed());
        return result.size() <= limit ? result : new ArrayList<>(result.subList(0, Math.max(0, limit)));
    }

    public void record(Transaction transaction) {
        if (transaction == null || transaction.playerId() == null) {
            return;
        }
        Account account = account(transaction.playerId(), transaction.playerName());
        synchronized (account) {
            account.lastKnownName = transaction.playerName() == null ? account.lastKnownName : transaction.playerName();
            account.games++;
            if (transaction.win()) {
                account.wins++;
            } else {
                account.losses++;
            }
            account.wagered = safeAdd(account.wagered, transaction.stake());
            account.paidOut = safeAdd(account.paidOut, transaction.payout());
            account.bestWin = Math.max(account.bestWin, Math.max(0.0, transaction.net()));
            account.history.addFirst(transaction);
            trim(account.history);
        }
    }

    public double jackpotPool() {
        return jackpotPool;
    }

    public double addToJackpot(double amount) {
        if (!finiteNonNegative(amount)) {
            return jackpotPool;
        }
        synchronized (saveLock) {
            double updated = jackpotPool + amount;
            if (Double.isFinite(updated)) {
                jackpotPool = updated;
            }
            return jackpotPool;
        }
    }

    public double claimJackpot(double resetTo) {
        synchronized (saveLock) {
            double claimed = jackpotPool;
            jackpotPool = finiteNonNegative(resetTo, settings.jackpotSeed());
            return claimed;
        }
    }

    public void restoreJackpot(double amount) {
        if (finiteNonNegative(amount)) {
            synchronized (saveLock) {
                jackpotPool = amount;
            }
        }
    }

    public void resetAccount(UUID uuid) {
        if (uuid != null) {
            accounts.remove(uuid);
        }
    }

    /** Queues a snapshot save and returns immediately. */
    public void saveAsync() {
        if (closed || writer.isShutdown()) {
            return;
        }
        try {
            writer.submit(this::saveNow);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Shutdown won the race; the synchronous final save covers it.
        }
    }

    /** Writes a complete, atomically replaced snapshot. Safe on shutdown. */
    public void saveNow() {
        if (closed && !writer.isShutdown()) {
            // A final save is still intentionally allowed during close().
        }
        Snapshot snapshot = snapshot();
        synchronized (saveLock) {
            try {
                File parent = dataFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
                    throw new IOException("Could not create " + parent);
                }
                YamlConfiguration data = new YamlConfiguration();
                data.set("format-version", 1);
                data.set("saved-at", Instant.now().toString());
                data.set("jackpot-pool", snapshot.jackpotPool);
                for (Map.Entry<UUID, AccountSnapshot> entry : snapshot.accounts.entrySet()) {
                    String path = "accounts." + entry.getKey();
                    AccountSnapshot account = entry.getValue();
                    data.set(path + ".last-known-name", account.lastKnownName);
                    data.set(path + ".fallback-balance", account.fallbackBalance);
                    data.set(path + ".stats.games", account.games);
                    data.set(path + ".stats.wins", account.wins);
                    data.set(path + ".stats.losses", account.losses);
                    data.set(path + ".stats.wagered", account.wagered);
                    data.set(path + ".stats.paid-out", account.paidOut);
                    data.set(path + ".stats.best-win", account.bestWin);
                    List<Map<String, Object>> history = new ArrayList<>();
                    for (Transaction transaction : account.history) {
                        history.add(Map.of(
                                "id", transaction.id().toString(),
                                "player-name", transaction.playerName(),
                                "game", transaction.game().id(),
                                "stake", transaction.stake(),
                                "payout", transaction.payout(),
                                "win", transaction.win(),
                                "details", transaction.details(),
                                "timestamp", transaction.timestamp().toEpochMilli()));
                    }
                    data.set(path + ".history", history);
                }

                File temporary = new File(dataFile.getParentFile(), dataFile.getName() + ".tmp");
                data.save(temporary);
                try {
                    Files.move(temporary.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException | RuntimeException exception) {
                plugin.getLogger().severe("Could not save data.yml: " + exception.getMessage());
            }
        }
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        saveNow();
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }

    private Snapshot snapshot() {
        ConcurrentHashMap<UUID, AccountSnapshot> copy = new ConcurrentHashMap<>();
        for (Map.Entry<UUID, Account> entry : accounts.entrySet()) {
            Account account = entry.getValue();
            synchronized (account) {
                copy.put(entry.getKey(), new AccountSnapshot(account.lastKnownName, account.fallbackBalance,
                        account.games, account.wins, account.losses, account.wagered, account.paidOut,
                        account.bestWin, List.copyOf(account.history)));
            }
        }
        return new Snapshot(jackpotPool, copy);
    }

    private Account account(UUID uuid, String name) {
        Account account = accounts.computeIfAbsent(uuid,
                ignored -> new Account(name == null || name.isBlank() ? "Unknown" : name, settings.fallbackStartingBalance()));
        if (name != null && !name.isBlank()) {
            synchronized (account) {
                account.lastKnownName = name;
            }
        }
        return account;
    }

    private Transaction readTransaction(UUID uuid, Map<?, ?> map) {
        try {
            GameType game = GameType.fromId(String.valueOf(map.get("game"))).orElse(null);
            if (game == null) {
                return null;
            }
            UUID id = UUID.fromString(String.valueOf(value(map, "id", UUID.randomUUID().toString())));
            Object timestampValue = value(map, "timestamp", 0L);
            long timestamp = timestampValue instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(timestampValue));
            return new Transaction(id, uuid, String.valueOf(value(map, "player-name", "Unknown")), game,
                    finiteNonNegative(number(map.get("stake")), 0.0), finiteNonNegative(number(map.get("payout")), 0.0),
                    Boolean.parseBoolean(String.valueOf(value(map, "win", false))),
                    String.valueOf(value(map, "details", "")),
                    timestamp <= 0 ? Instant.EPOCH : Instant.ofEpochMilli(timestamp));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Object value(Map<?, ?> map, String key, Object fallback) {
        return map.containsKey(key) && map.get(key) != null ? map.get(key) : fallback;
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
    }

    private void trim(Deque<Transaction> history) {
        while (history.size() > settings.historySize()) {
            history.removeLast();
        }
    }

    private static double safeAdd(double left, double right) {
        double result = left + Math.max(0.0, right);
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }

    private static boolean finitePositive(double value) {
        return Double.isFinite(value) && value > 0.0;
    }

    private static boolean finiteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }

    private static double finiteNonNegative(double value, double fallback) {
        return finiteNonNegative(value) ? value : fallback;
    }

    private static final class Account {
        private String lastKnownName;
        private double fallbackBalance;
        private long games;
        private long wins;
        private long losses;
        private double wagered;
        private double paidOut;
        private double bestWin;
        private final Deque<Transaction> history = new ArrayDeque<>();

        private Account(String lastKnownName, double fallbackBalance) {
            this.lastKnownName = lastKnownName == null || lastKnownName.isBlank() ? "Unknown" : lastKnownName;
            this.fallbackBalance = fallbackBalance;
        }
    }

    private record AccountSnapshot(String lastKnownName, double fallbackBalance, long games, long wins, long losses,
                                   double wagered, double paidOut, double bestWin, List<Transaction> history) {
    }

    private record Snapshot(double jackpotPool, Map<UUID, AccountSnapshot> accounts) {
    }
}
