package dev.superseller.playervault.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.superseller.playervault.model.VaultData;

/**
 * Single-file SQLite storage ({@code vaults.db}).
 *
 * <p>The driver is bundled inside the plugin jar, so this backend also works on
 * servers with no internet access. {@code org.xerial:sqlite-jdbc} ships native
 * libraries for Linux, Windows and macOS and picks the right one at runtime, which
 * is why the dependency is shaded <em>without</em> package relocation — the driver
 * resolves its natives by a package-relative resource path.
 *
 * <p>One shared connection guarded by an intrinsic lock keeps the implementation
 * simple and correct when several region threads save at the same time; vault
 * writes are small and infrequent, so this is never a bottleneck.
 */
public final class SqliteVaultStore implements VaultStore {

    private static final String TABLE = "player_vaults";
    private static final Set<String> JOURNAL_MODES =
            Set.of("DELETE", "TRUNCATE", "PERSISTENT", "MEMORY", "WAL", "OFF");

    private final Path database;
    private final Logger logger;
    private final int defaultRows;
    private final String journalMode;
    private final int busyTimeoutMs;
    private final Object lock = new Object();

    private Connection connection;

    public SqliteVaultStore(File dataFolder, Logger logger, int defaultRows, String journalMode, int busyTimeoutMs) {
        this.database = dataFolder.toPath().resolve("vaults.db");
        this.logger = logger;
        this.defaultRows = Math.max(1, defaultRows);
        this.journalMode = normaliseJournalMode(journalMode);
        this.busyTimeoutMs = Math.clamp(busyTimeoutMs, 0, 60_000);
    }

    private static String normaliseJournalMode(String raw) {
        String value = raw == null ? "WAL" : raw.trim().toUpperCase(Locale.ROOT);
        return JOURNAL_MODES.contains(value) ? value : "WAL";
    }

    @Override
    public String name() {
        return "SQLite (" + database.getFileName() + ")";
    }

    /** Opens the database and creates the schema when needed. */
    public void open() throws IOException {
        synchronized (lock) {
            connect();
        }
    }

    private void connect() throws SQLException, IOException {
        if (connection != null && !connection.isClosed()) {
            return;
        }
        Path parent = database.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new IOException("The bundled SQLite driver is missing from the plugin jar", ex);
        }
        // Forward slashes are accepted by SQLite on every platform, including Windows.
        String url = "jdbc:sqlite:" + database.toAbsolutePath().toString().replace('\\', '/');
        connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=" + busyTimeoutMs);
            statement.execute("PRAGMA journal_mode=" + journalMode);
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        uuid      TEXT PRIMARY KEY NOT NULL,
                        name      TEXT NOT NULL DEFAULT '',
                        rows      INTEGER NOT NULL DEFAULT 1,
                        purchased INTEGER NOT NULL DEFAULT 0,
                        spent     REAL NOT NULL DEFAULT 0,
                        updated   INTEGER NOT NULL DEFAULT 0,
                        items     TEXT NOT NULL DEFAULT ''
                    )
                    """.formatted(TABLE));
            migrate(statement);
        }
        logger.info("PlayerVault storage ready: " + name());
    }

    /**
     * Adds columns introduced after a database was first created, so upgrading the
     * plugin never requires deleting {@code vaults.db}.
     */
    private void migrate(Statement statement) throws SQLException {
        boolean hasPurchased = false;
        try (ResultSet columns = statement.executeQuery("PRAGMA table_info(" + TABLE + ")")) {
            while (columns.next()) {
                if ("purchased".equalsIgnoreCase(columns.getString("name"))) {
                    hasPurchased = true;
                    break;
                }
            }
        }
        if (!hasPurchased) {
            statement.execute("ALTER TABLE " + TABLE + " ADD COLUMN purchased INTEGER NOT NULL DEFAULT 0");
            logger.info("Added the 'purchased' column to " + TABLE + ".");
        }
    }

    private Connection connection() throws IOException {
        try {
            connect();
            return connection;
        } catch (SQLException ex) {
            throw new IOException("Could not open the vault database", ex);
        }
    }

    @Override
    public VaultData load(UUID owner) throws IOException {
        synchronized (lock) {
            String sql = "SELECT name, rows, purchased, spent, updated, items FROM " + TABLE + " WHERE uuid = ?";
            try (PreparedStatement statement = connection().prepareStatement(sql)) {
                statement.setString(1, owner.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return null;
                    }
                    int rows = Math.max(1, result.getInt("rows"));
                    VaultData data = new VaultData(owner, result.getString("name"), rows);
                    data.purchasedRows(result.getInt("purchased"));
                    data.totalSpent(result.getDouble("spent"));
                    data.contents(ItemCodec.decode(result.getString("items"),
                            rows * VaultData.SLOTS_PER_ROW, logger));
                    return data;
                }
            } catch (SQLException ex) {
                throw new IOException("Could not read the vault of " + owner, ex);
            }
        }
    }

    @Override
    public void save(VaultData data) throws IOException {
        String sql = "INSERT INTO " + TABLE
                + " (uuid, name, rows, purchased, spent, updated, items) VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(uuid) DO UPDATE SET "
                + "name = excluded.name, rows = excluded.rows, purchased = excluded.purchased, "
                + "spent = excluded.spent, updated = excluded.updated, items = excluded.items";
        synchronized (lock) {
            try (PreparedStatement statement = connection().prepareStatement(sql)) {
                statement.setString(1, data.owner().toString());
                statement.setString(2, data.name());
                statement.setInt(3, data.rows());
                statement.setInt(4, data.purchasedRows());
                statement.setDouble(5, data.totalSpent());
                statement.setLong(6, data.updated());
                statement.setString(7, ItemCodec.encode(data.contents()));
                statement.executeUpdate();
            } catch (SQLException ex) {
                throw new IOException("Could not write the vault of " + data.owner(), ex);
            }
        }
    }

    @Override
    public void delete(UUID owner) throws IOException {
        synchronized (lock) {
            String sql = "DELETE FROM " + TABLE + " WHERE uuid = ?";
            try (PreparedStatement statement = connection().prepareStatement(sql)) {
                statement.setString(1, owner.toString());
                statement.executeUpdate();
            } catch (SQLException ex) {
                throw new IOException("Could not delete the vault of " + owner, ex);
            }
        }
    }

    @Override
    public List<UUID> all() throws IOException {
        List<UUID> owners = new ArrayList<>();
        synchronized (lock) {
            String sql = "SELECT uuid FROM " + TABLE + " ORDER BY uuid";
            try (PreparedStatement statement = connection().prepareStatement(sql);
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    try {
                        owners.add(UUID.fromString(result.getString("uuid")));
                    } catch (IllegalArgumentException ignored) {
                        logger.log(Level.WARNING, "Skipping a vault row with an unparsable UUID.");
                    }
                }
            } catch (SQLException ex) {
                throw new IOException("Could not list stored vaults", ex);
            }
        }
        return owners;
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (connection == null) {
                return;
            }
            try {
                connection.close();
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Could not close the vault database cleanly.", ex);
            } finally {
                connection = null;
            }
        }
    }
}
