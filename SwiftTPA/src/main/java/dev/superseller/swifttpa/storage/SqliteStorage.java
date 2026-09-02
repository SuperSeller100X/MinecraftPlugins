package dev.superseller.swifttpa.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * SQLite storage in {@code swifttpa.db}. Single portable file that behaves
 * the same on Linux, Windows and macOS. The in-memory cache is the runtime
 * source of truth; writes go through immediately under a lock. The sqlite-jdbc
 * driver is shaded into the plugin jar, so no server-side setup is needed.
 */
public final class SqliteStorage implements PlayerDataStorage {

    private final File file;
    private final Logger logger;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private Connection connection;

    public SqliteStorage(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    @Override
    public void init() {
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
                        CREATE TABLE IF NOT EXISTS swifttpa_players (
                          uuid TEXT PRIMARY KEY,
                          enabled INTEGER NOT NULL DEFAULT 1,
                          blocked TEXT NOT NULL DEFAULT '',
                          sent INTEGER NOT NULL DEFAULT 0,
                          accepted INTEGER NOT NULL DEFAULT 0,
                          denied INTEGER NOT NULL DEFAULT 0,
                          teleported INTEGER NOT NULL DEFAULT 0
                        )""");
            }
        } catch (ClassNotFoundException | SQLException e) {
            logger.severe("Failed to open swifttpa.db: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PlayerData data(UUID uuid) {
        return cache.computeIfAbsent(uuid, PlayerData::new);
    }

    @Override
    public void loadPlayer(UUID uuid) {
        lock.lock();
        try {
            if (connection == null) {
                return;
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT enabled, blocked, sent, accepted, denied, teleported FROM swifttpa_players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return;
                    }
                    PlayerData data = data(uuid);
                    data.setRequestsEnabled(rs.getInt("enabled") != 0);
                    data.loadBlocked(split(rs.getString("blocked")));
                    data.setStats(rs.getLong("sent"), rs.getLong("accepted"),
                            rs.getLong("denied"), rs.getLong("teleported"));
                }
            }
        } catch (SQLException e) {
            logger.warning("Could not load " + uuid + ": " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void savePlayer(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) {
            return;
        }
        lock.lock();
        try {
            if (connection == null) {
                return;
            }
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO swifttpa_players (uuid, enabled, blocked, sent, accepted, denied, teleported)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                      enabled = excluded.enabled,
                      blocked = excluded.blocked,
                      sent = excluded.sent,
                      accepted = excluded.accepted,
                      denied = excluded.denied,
                      teleported = excluded.teleported""")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, data.requestsEnabled() ? 1 : 0);
                ps.setString(3, String.join(",", data.serializeBlocked()));
                ps.setLong(4, data.sentCount());
                ps.setLong(5, data.acceptedCount());
                ps.setLong(6, data.deniedCount());
                ps.setLong(7, data.teleportedCount());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.warning("Could not save " + uuid + ": " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void saveAll() {
        for (UUID uuid : cache.keySet()) {
            savePlayer(uuid);
        }
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
            logger.warning("Could not close swifttpa.db: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private static List<String> split(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.asList(joined.split(","));
    }
}
