package dev.superseller.veltryx.storage;

import dev.superseller.veltryx.model.Account;
import dev.superseller.veltryx.model.BanEntry;
import dev.superseller.veltryx.model.FlagEntry;
import dev.superseller.veltryx.model.MuteEntry;
import dev.superseller.veltryx.model.NoteEntry;
import dev.superseller.veltryx.model.Profile;
import dev.superseller.veltryx.model.ReportEntry;
import dev.superseller.veltryx.model.WarnEntry;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite-backed {@link DataStore}: one database file with in-memory caches and
 * write-through upserts. Recommended for large servers and networks.
 */
public final class SqliteDataStore implements DataStore {

    private final File databaseFile;
    private final Logger logger;

    private final ConcurrentMap<UUID, Account> accounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Profile> profiles = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, BanEntry> bans = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, MuteEntry> mutes = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WarnEntry> warns = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, NoteEntry> notes = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ReportEntry> reports = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, FlagEntry> flags = new ConcurrentHashMap<>();

    private final Object lock = new Object();
    private Connection connection;

    public SqliteDataStore(File databaseFile, Logger logger) {
        this.databaseFile = databaseFile;
        this.logger = logger;
    }

    @Override
    public void load() {
        synchronized (lock) {
            try {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE IF NOT EXISTS vel_accounts ("
                            + "uuid TEXT PRIMARY KEY, name TEXT, balance INTEGER)");
                    statement.execute("CREATE TABLE IF NOT EXISTS vel_profiles ("
                            + "uuid TEXT PRIMARY KEY, name TEXT, first_seen INTEGER, last_seen INTEGER,"
                            + " playtime INTEGER, joins INTEGER)");
                    statement.execute("CREATE TABLE IF NOT EXISTS vel_bans ("
                            + "id TEXT PRIMARY KEY, uuid TEXT, name TEXT, category TEXT, reason TEXT,"
                            + " details TEXT, staff TEXT, issued INTEGER, expires INTEGER,"
                            + " active INTEGER, auto INTEGER)");
                    statement.execute("CREATE TABLE IF NOT EXISTS vel_mutes ("
                            + "id TEXT PRIMARY KEY, uuid TEXT, name TEXT, reason TEXT, staff TEXT,"
                            + " issued INTEGER, expires INTEGER, active INTEGER)");
                    statement.execute("CREATE TABLE IF NOT EXISTS vel_warns ("
                            + "id TEXT PRIMARY KEY, uuid TEXT, name TEXT, reason TEXT, staff TEXT,"
                            + " issued INTEGER, active INTEGER)");
                    statement.execute("CREATE TABLE IF NOT EXISTS vel_notes ("
                            + "id TEXT PRIMARY KEY, uuid TEXT, name TEXT, note TEXT, staff TEXT,"
                            + " issued INTEGER)");
                    statement.execute("CREATE TABLE IF NOT EXISTS vel_reports ("
                            + "id TEXT PRIMARY KEY, target TEXT, target_name TEXT, reporter TEXT,"
                            + " reporter_name TEXT, reason TEXT, issued INTEGER, resolved INTEGER)");
                    statement.execute("CREATE TABLE IF NOT EXISTS vel_flags ("
                            + "id TEXT PRIMARY KEY, uuid TEXT, name TEXT, check_name TEXT, vl REAL,"
                            + " detail TEXT, issued INTEGER)");
                }
                loadFromDatabase();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Could not open SQLite database: " + e.getMessage());
            }
        }
    }

    private void loadFromDatabase() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery("SELECT uuid, name, balance FROM vel_accounts");
            while (rs.next()) {
                UUID uuid = parseUuid(rs.getString(1));
                if (uuid != null) {
                    accounts.put(uuid, new Account(uuid, rs.getString(2), rs.getLong(3)));
                }
            }
            rs = statement.executeQuery("SELECT uuid, name, first_seen, last_seen, playtime, joins FROM vel_profiles");
            while (rs.next()) {
                UUID uuid = parseUuid(rs.getString(1));
                if (uuid != null) {
                    profiles.put(uuid, new Profile(uuid, rs.getString(2), rs.getLong(3),
                            rs.getLong(4), rs.getLong(5), rs.getInt(6)));
                }
            }
            rs = statement.executeQuery("SELECT id, uuid, name, category, reason, details, staff,"
                    + " issued, expires, active, auto FROM vel_bans");
            while (rs.next()) {
                UUID uuid = parseUuid(rs.getString(2));
                if (uuid != null) {
                    bans.put(rs.getString(1), new BanEntry(rs.getString(1), uuid, rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                            rs.getLong(8), rs.getLong(9), rs.getInt(10) != 0, rs.getInt(11) != 0));
                }
            }
            rs = statement.executeQuery("SELECT id, uuid, name, reason, staff, issued, expires, active FROM vel_mutes");
            while (rs.next()) {
                UUID uuid = parseUuid(rs.getString(2));
                if (uuid != null) {
                    mutes.put(rs.getString(1), new MuteEntry(rs.getString(1), uuid, rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getLong(6), rs.getLong(7),
                            rs.getInt(8) != 0));
                }
            }
            rs = statement.executeQuery("SELECT id, uuid, name, reason, staff, issued, active FROM vel_warns");
            while (rs.next()) {
                UUID uuid = parseUuid(rs.getString(2));
                if (uuid != null) {
                    warns.put(rs.getString(1), new WarnEntry(rs.getString(1), uuid, rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getLong(6), rs.getInt(7) != 0));
                }
            }
            rs = statement.executeQuery("SELECT id, uuid, name, note, staff, issued FROM vel_notes");
            while (rs.next()) {
                UUID uuid = parseUuid(rs.getString(2));
                if (uuid != null) {
                    notes.put(rs.getString(1), new NoteEntry(rs.getString(1), uuid, rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getLong(6)));
                }
            }
            rs = statement.executeQuery("SELECT id, target, target_name, reporter, reporter_name,"
                    + " reason, issued, resolved FROM vel_reports");
            while (rs.next()) {
                UUID target = parseUuid(rs.getString(2));
                UUID reporter = parseUuid(rs.getString(4));
                if (target != null && reporter != null) {
                    reports.put(rs.getString(1), new ReportEntry(rs.getString(1), target, rs.getString(3),
                            reporter, rs.getString(5), rs.getString(6), rs.getLong(7), rs.getInt(8) != 0));
                }
            }
            rs = statement.executeQuery("SELECT id, uuid, name, check_name, vl, detail, issued FROM vel_flags");
            while (rs.next()) {
                UUID uuid = parseUuid(rs.getString(2));
                if (uuid != null) {
                    flags.put(rs.getString(1), new FlagEntry(rs.getString(1), uuid, rs.getString(3),
                            rs.getString(4), rs.getDouble(5), rs.getString(6), rs.getLong(7)));
                }
            }
        }
    }

    @Override
    public void save() {
        // SQLite writes through on every mutation; nothing to flush.
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    logger.log(Level.WARNING, "Could not close SQLite database: " + e.getMessage());
                }
                connection = null;
            }
        }
    }

    // ------------------------------------------------------------- economy

    @Override
    public long getBalance(UUID uuid) {
        Account account = accounts.get(uuid);
        return account == null ? 0L : account.balance();
    }

    @Override
    public void setBalance(UUID uuid, String name, long cents) {
        long balance = Math.max(0L, cents);
        accounts.put(uuid, new Account(uuid, name, balance));
        execute("INSERT INTO vel_accounts (uuid, name, balance) VALUES (?, ?, ?)"
                        + " ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, balance = excluded.balance",
                uuid.toString(), name, balance);
    }

    @Override
    public List<Account> top(int limit) {
        List<Account> sorted = new ArrayList<>(accounts.values());
        sorted.sort(Comparator.comparingLong(Account::balance).reversed());
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    // ------------------------------------------------------------- profile

    @Override
    public Profile getProfile(UUID uuid) {
        return profiles.get(uuid);
    }

    @Override
    public void upsertProfile(UUID uuid, String name, long now, long sessionMillis, boolean joined) {
        Profile existing = profiles.get(uuid);
        Profile updated;
        if (existing == null) {
            updated = new Profile(uuid, name, now, now, Math.max(0L, sessionMillis), joined ? 1 : 0);
        } else {
            updated = new Profile(uuid, name,
                    existing.firstSeen() == 0L ? now : existing.firstSeen(),
                    joined ? now : Math.max(existing.lastSeen(), now),
                    existing.playtimeMs() + Math.max(0L, sessionMillis),
                    existing.joins() + (joined ? 1 : 0));
        }
        profiles.put(uuid, updated);
        execute("INSERT INTO vel_profiles (uuid, name, first_seen, last_seen, playtime, joins)"
                        + " VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET name = excluded.name,"
                        + " first_seen = excluded.first_seen, last_seen = excluded.last_seen,"
                        + " playtime = excluded.playtime, joins = excluded.joins",
                uuid.toString(), updated.name(), updated.firstSeen(), updated.lastSeen(),
                updated.playtimeMs(), updated.joins());
    }

    // --------------------------------------------------------------- bans

    @Override
    public void saveBan(BanEntry entry) {
        bans.put(entry.id(), entry);
        execute("INSERT INTO vel_bans (id, uuid, name, category, reason, details, staff, issued,"
                        + " expires, active, auto) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                        + " ON CONFLICT(id) DO UPDATE SET uuid = excluded.uuid, name = excluded.name,"
                        + " category = excluded.category, reason = excluded.reason, details = excluded.details,"
                        + " staff = excluded.staff, issued = excluded.issued, expires = excluded.expires,"
                        + " active = excluded.active, auto = excluded.auto",
                entry.id(), entry.uuid().toString(), entry.name(), entry.category(), entry.reason(),
                entry.details(), entry.staff(), entry.issued(), entry.expires(),
                entry.active() ? 1 : 0, entry.auto() ? 1 : 0);
    }

    @Override
    public void setBanActive(String id, boolean active) {
        BanEntry entry = bans.get(id);
        if (entry == null) {
            return;
        }
        BanEntry updated = new BanEntry(entry.id(), entry.uuid(), entry.name(), entry.category(),
                entry.reason(), entry.details(), entry.staff(), entry.issued(),
                entry.expires(), active, entry.auto());
        bans.put(id, updated);
        execute("UPDATE vel_bans SET active = ? WHERE id = ?", active ? 1 : 0, id);
    }

    @Override
    public List<BanEntry> allBans() {
        return List.copyOf(bans.values());
    }

    // --------------------------------------------------------------- mutes

    @Override
    public void saveMute(MuteEntry entry) {
        mutes.put(entry.id(), entry);
        execute("INSERT INTO vel_mutes (id, uuid, name, reason, staff, issued, expires, active)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET"
                        + " uuid = excluded.uuid, name = excluded.name, reason = excluded.reason,"
                        + " staff = excluded.staff, issued = excluded.issued, expires = excluded.expires,"
                        + " active = excluded.active",
                entry.id(), entry.uuid().toString(), entry.name(), entry.reason(), entry.staff(),
                entry.issued(), entry.expires(), entry.active() ? 1 : 0);
    }

    @Override
    public void setMuteActive(String id, boolean active) {
        MuteEntry entry = mutes.get(id);
        if (entry == null) {
            return;
        }
        MuteEntry updated = new MuteEntry(entry.id(), entry.uuid(), entry.name(), entry.reason(),
                entry.staff(), entry.issued(), entry.expires(), active);
        mutes.put(id, updated);
        execute("UPDATE vel_mutes SET active = ? WHERE id = ?", active ? 1 : 0, id);
    }

    @Override
    public List<MuteEntry> allMutes() {
        return List.copyOf(mutes.values());
    }

    // -------------------------------------------------------------- warns

    @Override
    public void saveWarn(WarnEntry entry) {
        warns.put(entry.id(), entry);
        execute("INSERT INTO vel_warns (id, uuid, name, reason, staff, issued, active)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET"
                        + " uuid = excluded.uuid, name = excluded.name, reason = excluded.reason,"
                        + " staff = excluded.staff, issued = excluded.issued, active = excluded.active",
                entry.id(), entry.uuid().toString(), entry.name(), entry.reason(), entry.staff(),
                entry.issued(), entry.active() ? 1 : 0);
    }

    @Override
    public void setWarnActive(String id, boolean active) {
        WarnEntry entry = warns.get(id);
        if (entry == null) {
            return;
        }
        WarnEntry updated = new WarnEntry(entry.id(), entry.uuid(), entry.name(), entry.reason(),
                entry.staff(), entry.issued(), active);
        warns.put(id, updated);
        execute("UPDATE vel_warns SET active = ? WHERE id = ?", active ? 1 : 0, id);
    }

    @Override
    public List<WarnEntry> allWarns() {
        return List.copyOf(warns.values());
    }

    // -------------------------------------------------------------- notes

    @Override
    public void saveNote(NoteEntry entry) {
        notes.put(entry.id(), entry);
        execute("INSERT INTO vel_notes (id, uuid, name, note, staff, issued) VALUES (?, ?, ?, ?, ?, ?)"
                        + " ON CONFLICT(id) DO UPDATE SET uuid = excluded.uuid, name = excluded.name,"
                        + " note = excluded.note, staff = excluded.staff, issued = excluded.issued",
                entry.id(), entry.uuid().toString(), entry.name(), entry.note(), entry.staff(),
                entry.issued());
    }

    @Override
    public void removeNote(String id) {
        notes.remove(id);
        execute("DELETE FROM vel_notes WHERE id = ?", id);
    }

    @Override
    public List<NoteEntry> allNotes() {
        return List.copyOf(notes.values());
    }

    // ------------------------------------------------------------- reports

    @Override
    public void saveReport(ReportEntry entry) {
        reports.put(entry.id(), entry);
        execute("INSERT INTO vel_reports (id, target, target_name, reporter, reporter_name, reason,"
                        + " issued, resolved) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET"
                        + " target = excluded.target, target_name = excluded.target_name,"
                        + " reporter = excluded.reporter, reporter_name = excluded.reporter_name,"
                        + " reason = excluded.reason, issued = excluded.issued, resolved = excluded.resolved",
                entry.id(), entry.target().toString(), entry.targetName(), entry.reporter().toString(),
                entry.reporterName(), entry.reason(), entry.issued(), entry.resolved() ? 1 : 0);
    }

    @Override
    public void setReportResolved(String id, boolean resolved) {
        ReportEntry entry = reports.get(id);
        if (entry == null) {
            return;
        }
        ReportEntry updated = new ReportEntry(entry.id(), entry.target(), entry.targetName(),
                entry.reporter(), entry.reporterName(), entry.reason(), entry.issued(), resolved);
        reports.put(id, updated);
        execute("UPDATE vel_reports SET resolved = ? WHERE id = ?", resolved ? 1 : 0, id);
    }

    @Override
    public List<ReportEntry> allReports() {
        return List.copyOf(reports.values());
    }

    // --------------------------------------------------------------- flags

    @Override
    public void saveFlag(FlagEntry entry) {
        flags.put(entry.id(), entry);
        execute("INSERT INTO vel_flags (id, uuid, name, check_name, vl, detail, issued)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET"
                        + " uuid = excluded.uuid, name = excluded.name, check_name = excluded.check_name,"
                        + " vl = excluded.vl, detail = excluded.detail, issued = excluded.issued",
                entry.id(), entry.uuid().toString(), entry.name(), entry.check(), entry.vl(),
                entry.detail(), entry.issued());
    }

    @Override
    public List<FlagEntry> flagsFor(UUID uuid) {
        List<FlagEntry> matching = new ArrayList<>();
        for (FlagEntry flag : flags.values()) {
            if (flag.uuid().equals(uuid)) {
                matching.add(flag);
            }
        }
        matching.sort(Comparator.comparingLong(FlagEntry::issued).reversed());
        return matching;
    }

    @Override
    public void pruneFlags(long olderThanMillis) {
        flags.values().removeIf(f -> f.issued() < olderThanMillis);
        execute("DELETE FROM vel_flags WHERE issued < ?", olderThanMillis);
    }

    // -------------------------------------------------------------- helper

    private void execute(String sql, Object... args) {
        synchronized (lock) {
            if (connection == null) {
                return;
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) {
                    Object arg = args[i];
                    if (arg instanceof String s) {
                        statement.setString(i + 1, s);
                    } else if (arg instanceof Long l) {
                        statement.setLong(i + 1, l);
                    } else if (arg instanceof Integer n) {
                        statement.setInt(i + 1, n);
                    } else if (arg instanceof Double d) {
                        statement.setDouble(i + 1, d);
                    } else {
                        statement.setNull(i + 1, java.sql.Types.NULL);
                    }
                }
                statement.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "SQLite update failed: " + e.getMessage());
            }
        }
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
