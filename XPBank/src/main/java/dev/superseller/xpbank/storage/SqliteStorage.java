package dev.superseller.xpbank.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * SQLite storage in {@code xpbank.db}. Single portable file that behaves the
 * same on Linux, Windows and macOS. In-memory maps are the runtime source of
 * truth; every mutation is written through immediately under a lock.
 */
public final class SqliteStorage implements BankStorage {

    private final File file;
    private final Logger logger;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private Connection connection;

    public SqliteStorage(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    @Override
    public void load() {
        lock.lock();
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                logger.warning("Could not create data folder " + parent);
            }
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA busy_timeout=5000");
                s.execute("PRAGMA synchronous=NORMAL");
                s.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS accounts (
                          uuid TEXT PRIMARY KEY,
                          name TEXT,
                          balance INTEGER NOT NULL DEFAULT 0
                        )""");
            }
            balances.clear();
            names.clear();
            try (Statement s = connection.createStatement();
                 ResultSet rs = s.executeQuery("SELECT uuid, name, balance FROM accounts")) {
                while (rs.next()) {
                    try {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        balances.put(uuid, Math.max(0L, rs.getLong("balance")));
                        String name = rs.getString("name");
                        if (name != null) {
                            names.put(uuid, name);
                        }
                    } catch (IllegalArgumentException ex) {
                        logger.warning("Skipping invalid account row: " + ex.getMessage());
                    }
                }
            }
        } catch (ClassNotFoundException | SQLException e) {
            logger.severe("Failed to open xpbank.db: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void save() {
        // Write-through model keeps the DB current; nothing buffered to flush.
    }

    @Override
    public void close() {
        lock.lock();
        try {
            if (connection != null) {
                connection.close();
                connection = null;
            }
        } catch (SQLException e) {
            logger.warning("Error closing xpbank.db: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long get(UUID uuid) {
        return balances.getOrDefault(uuid, 0L);
    }

    @Override
    public void set(UUID uuid, String name, long amount) {
        long safe = Math.max(0L, amount);
        balances.put(uuid, safe);
        if (name != null) {
            names.put(uuid, name);
        }
        String finalName = name != null ? name : names.get(uuid);
        lock.lock();
        try {
            if (connection == null) {
                return;
            }
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO accounts (uuid, name, balance) VALUES (?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET name = COALESCE(excluded.name, accounts.name),
                                                    balance = excluded.balance""")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, finalName);
                ps.setLong(3, safe);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Failed to write account " + uuid + ": " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void rememberName(UUID uuid, String name) {
        if (name == null) {
            return;
        }
        names.put(uuid, name);
        lock.lock();
        try {
            if (connection == null) {
                return;
            }
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO accounts (uuid, name, balance) VALUES (?, ?, 0)
                    ON CONFLICT(uuid) DO UPDATE SET name = excluded.name""")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.warning("Failed to store name for " + uuid + ": " + e.getMessage());
        } finally {
            lock.unlock();
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
            list.add(new Entry(e.getKey(), names.get(e.getKey()), e.getValue()));
        }
        list.sort((a, b) -> Long.compare(b.amount(), a.amount()));
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
