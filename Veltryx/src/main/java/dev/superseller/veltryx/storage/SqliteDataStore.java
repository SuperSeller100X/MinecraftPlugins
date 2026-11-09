package dev.superseller.veltryx.storage;

import dev.superseller.veltryx.model.Account;
import dev.superseller.veltryx.model.AhListing;
import dev.superseller.veltryx.model.BuyOrder;
import dev.superseller.veltryx.model.HomeLocation;
import dev.superseller.veltryx.model.BanEntry;
import dev.superseller.veltryx.model.FlagEntry;
import dev.superseller.veltryx.model.MuteEntry;
import dev.superseller.veltryx.model.NoteEntry;
import dev.superseller.veltryx.model.Profile;
import dev.superseller.veltryx.model.ReportEntry;
import dev.superseller.veltryx.model.WarnEntry;
import java.io.File;
import java.math.BigInteger;
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
    private final ConcurrentMap<String, AhListing> ahListings = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, BuyOrder> buyOrders = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, HomeLocation> homes = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> homeIcons = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> shards = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, long[]> stats = new ConcurrentHashMap<>();   // [kills, deaths]

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
                            + "uuid TEXT PRIMARY KEY, name TEXT, balance INTEGER, balance_exact TEXT)");
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
                    statement.execute("CREATE TABLE IF NOT EXISTS vel_ah ("
                            + "id TEXT PRIMARY KEY, seller TEXT, seller_name TEXT, material TEXT,"
                            + " amount INTEGER, price INTEGER, listed INTEGER, expires INTEGER,"
                            + " closed INTEGER)");
                    statement.execute("CREATE TABLE IF NOT EXISTS vel_orders ("
                            + "id TEXT PRIMARY KEY, buyer TEXT, buyer_name TEXT, material TEXT,"
                            + " amount INTEGER, filled INTEGER, price_each INTEGER, created INTEGER,"
                            + " expires INTEGER, closed INTEGER, collected INTEGER NOT NULL DEFAULT 0)");
                    statement.execute("CREATE TABLE IF NOT EXISTS vel_homes ("
                            + "owner TEXT, name TEXT, world TEXT, x REAL, y REAL, z REAL,"
                            + " yaw REAL, pitch REAL, icon TEXT, PRIMARY KEY (owner, name))");
                    statement.execute("CREATE TABLE IF NOT EXISTS vel_shards ("
                            + "uuid TEXT PRIMARY KEY, shards INTEGER)");
                    statement.execute("CREATE TABLE IF NOT EXISTS vel_stats ("
                            + "uuid TEXT PRIMARY KEY, kills INTEGER, deaths INTEGER)");
                    boolean exactBalanceColumn = false;
                    try (ResultSet columns = statement.executeQuery("PRAGMA table_info(vel_accounts)")) {
                        while (columns.next()) if ("balance_exact".equals(columns.getString("name"))) exactBalanceColumn = true;
                    }
                    if (!exactBalanceColumn) statement.execute("ALTER TABLE vel_accounts ADD COLUMN balance_exact TEXT");
                    boolean ahExactColumn = false, ahItemColumn = false, orderTotalColumn = false;
                    try (ResultSet columns = statement.executeQuery("PRAGMA table_info(vel_ah)")) {
                        while (columns.next()) {
                            if ("price_exact".equals(columns.getString("name"))) ahExactColumn = true;
                            if ("item_data".equals(columns.getString("name"))) ahItemColumn = true;
                        }
                    }
                    if (!ahExactColumn) statement.execute("ALTER TABLE vel_ah ADD COLUMN price_exact TEXT");
                    if (!ahItemColumn) statement.execute("ALTER TABLE vel_ah ADD COLUMN item_data TEXT");
                    try (ResultSet columns = statement.executeQuery("PRAGMA table_info(vel_orders)")) {
                        while (columns.next()) if ("total_exact".equals(columns.getString("name"))) orderTotalColumn = true;
                    }
                    if (!orderTotalColumn) statement.execute("ALTER TABLE vel_orders ADD COLUMN total_exact TEXT");
                    boolean collectedColumn = false;
                    try (ResultSet columns = statement.executeQuery("PRAGMA table_info(vel_orders)")) {
                        while (columns.next()) if ("collected".equals(columns.getString("name"))) collectedColumn = true;
                    }
                    if (!collectedColumn) {
                        // Existing releases never recorded deliveries. Mark historical fills as
                        // reconciled rather than minting items that might have been compensated.
                        connection.setAutoCommit(false);
                        try {
                            statement.execute("ALTER TABLE vel_orders ADD COLUMN collected INTEGER NOT NULL DEFAULT 0");
                            int legacy = statement.executeUpdate("UPDATE vel_orders SET collected = filled WHERE filled > 0");
                            connection.commit();
                            if (legacy > 0) logger.warning("Migrated " + legacy + " legacy filled orders with unknown delivery; reconcile manually.");
                        } catch (SQLException ex) {
                            try { connection.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                            throw ex;
                        } finally { connection.setAutoCommit(true); }
                    }
                    try {
                        statement.execute("ALTER TABLE vel_homes ADD COLUMN icon TEXT");
                    } catch (SQLException ignored) {
                        // column already exists
                    }
                }
                loadFromDatabase();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Could not open SQLite database", e);
                throw new IllegalStateException("Could not load SQLite data safely", e);
            }
        }
    }

    private void loadFromDatabase() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery("SELECT uuid, name, balance, balance_exact FROM vel_accounts");
            while (rs.next()) {
                UUID uuid = parseUuid(rs.getString(1));
                if (uuid != null) {
                    String exact = rs.getString(4);
                    BigInteger cents;
                    try {
                        cents = exact == null ? BigInteger.valueOf(rs.getLong(3)) : new BigInteger(exact);
                        if (cents.signum() < 0) throw new NumberFormatException("negative balance");
                    } catch (NumberFormatException ex) { throw new SQLException("Invalid account balance for " + uuid, ex); }
                    accounts.put(uuid, new Account(uuid, rs.getString(2), cents));
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
            rs = statement.executeQuery("SELECT id, seller, seller_name, material, amount, price,"
                    + " listed, expires, closed, price_exact, item_data FROM vel_ah");
            while (rs.next()) {
                UUID seller = parseUuid(rs.getString(2));
                if (seller == null) throw new SQLException("Invalid auction seller " + rs.getString(1));
                String exact = rs.getString(10);
                BigInteger price = exact == null ? BigInteger.valueOf(rs.getLong(6)) : new BigInteger(exact);
                ahListings.put(rs.getString(1), new AhListing(rs.getString(1), seller,
                        rs.getString(3), rs.getString(4), rs.getInt(5), price,
                        rs.getLong(7), rs.getLong(8), rs.getInt(9) != 0, rs.getString(11)));
            }
            rs = statement.executeQuery("SELECT id, buyer, buyer_name, material, amount, filled,"
                    + " price_each, created, expires, closed, collected, total_exact FROM vel_orders");
            while (rs.next()) {
                UUID buyer = parseUuid(rs.getString(2));
                if (buyer == null || rs.getString(5) == null || rs.getString(6) == null
                        || rs.getString(7) == null || rs.getString(11) == null)
                    throw new SQLException("Invalid persisted buy order " + rs.getString(1));
                String totalExact = rs.getString(12);
                BigInteger total = totalExact == null
                        ? BigInteger.valueOf(rs.getLong(7)).multiply(BigInteger.valueOf(rs.getInt(5)))
                        : new BigInteger(totalExact);
                buyOrders.put(rs.getString(1), new BuyOrder(rs.getString(1), buyer,
                        rs.getString(3), rs.getString(4), rs.getInt(5), rs.getInt(6), total,
                        rs.getLong(8), rs.getLong(9), rs.getInt(10) != 0, rs.getInt(11)));
            }
            rs = statement.executeQuery("SELECT owner, name, world, x, y, z, yaw, pitch, icon FROM vel_homes");
            while (rs.next()) {
                UUID owner = parseUuid(rs.getString(1));
                if (owner != null) {
                    homes.put(rs.getString(1) + ":" + rs.getString(2), new HomeLocation(owner,
                            rs.getString(2), rs.getString(3), rs.getDouble(4), rs.getDouble(5),
                            rs.getDouble(6), (float) rs.getDouble(7), (float) rs.getDouble(8)));
                    String icon = rs.getString(9);
                    if (icon != null && !icon.isEmpty()) {
                        homeIcons.put(rs.getString(1) + ":" + rs.getString(2), icon);
                    }
                }
            }
            rs = statement.executeQuery("SELECT uuid, shards FROM vel_shards");
            while (rs.next()) {
                UUID uuid = parseUuid(rs.getString(1));
                if (uuid != null) {
                    shards.put(uuid, rs.getLong(2));
                }
            }
            rs = statement.executeQuery("SELECT uuid, kills, deaths FROM vel_stats");
            while (rs.next()) {
                UUID uuid = parseUuid(rs.getString(1));
                if (uuid != null) {
                    stats.put(uuid, new long[]{rs.getLong(2), rs.getLong(3)});
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

    @Override public long getBalance(UUID uuid) {
        return dev.superseller.veltryx.util.Money.legacyLong(getBalanceExact(uuid));
    }
    @Override public BigInteger getBalanceExact(UUID uuid) {
        Account account = accounts.get(uuid);
        return account == null ? BigInteger.ZERO : account.balanceExact();
    }
    @Override public void setBalance(UUID uuid, String name, long cents) {
        setBalanceExact(uuid, name, BigInteger.valueOf(Math.max(0L, cents)));
    }
    @Override public void setBalanceExact(UUID uuid, String name, BigInteger cents) {
        setBalancesExact(List.of(new Account(uuid, name, cents)));
    }
    @Override public void setBalancesExact(List<Account> updates) {
        synchronized (lock) {
            if (connection == null) throw new IllegalStateException("SQLite economy is unavailable");
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO vel_accounts (uuid, name, balance, balance_exact) VALUES (?, ?, ?, ?)"
                        + " ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, balance = excluded.balance,"
                        + " balance_exact = excluded.balance_exact")) {
                    for (Account account : updates) {
                        ps.setString(1, account.uuid().toString()); ps.setString(2, account.name());
                        ps.setLong(3, account.balance()); ps.setString(4, account.balanceExact().toString());
                        ps.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException ex) {
                try { connection.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                throw new IllegalStateException("Could not persist economy accounts", ex);
            } finally {
                try { connection.setAutoCommit(true); }
                catch (SQLException ex) { logger.log(Level.SEVERE, "Could not restore SQLite auto-commit", ex); }
            }
            for (Account account : updates) accounts.put(account.uuid(), account);
        }
    }
    @Override public List<Account> top(int limit) {
        List<Account> sorted = new ArrayList<>(accounts.values());
        sorted.sort(Comparator.comparing(Account::balanceExact).reversed());
        return sorted.subList(0, Math.min(Math.max(0, limit), sorted.size()));
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

    // --------------------------------------------------- market & homes

    @Override
    public List<AhListing> allAhListings() {
        return List.copyOf(ahListings.values());
    }

    @Override
    public void saveAhListing(AhListing entry) {
        synchronized (lock) {
            if (connection == null) throw new IllegalStateException("SQLite market is unavailable");
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO vel_ah (id, seller, seller_name, material, amount, price, listed,"
                    + " expires, closed, price_exact, item_data) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    + " ON CONFLICT(id) DO UPDATE SET seller=excluded.seller, seller_name=excluded.seller_name,"
                    + " material=excluded.material, amount=excluded.amount, price=excluded.price, listed=excluded.listed,"
                    + " expires=excluded.expires, closed=excluded.closed, price_exact=excluded.price_exact, item_data=excluded.item_data")) {
                ps.setString(1, entry.id()); ps.setString(2, entry.seller().toString());
                ps.setString(3, entry.sellerName()); ps.setString(4, entry.material());
                ps.setInt(5, entry.amount()); ps.setLong(6, dev.superseller.veltryx.util.Money.legacyLong(entry.priceExactCents()));
                ps.setLong(7, entry.listed()); ps.setLong(8, entry.expires()); ps.setInt(9, entry.closed() ? 1 : 0);
                ps.setString(10, entry.priceExactCents().toString()); ps.setString(11, entry.itemData());
                ps.executeUpdate();
            } catch (SQLException ex) { throw new IllegalStateException("Could not persist auction listing", ex); }
            ahListings.put(entry.id(), entry);
        }
    }

    @Override
    public List<BuyOrder> allBuyOrders() {
        return List.copyOf(buyOrders.values());
    }

    @Override
    public void saveBuyOrder(BuyOrder entry) {
        synchronized (lock) {
            if (connection == null) throw new IllegalStateException("SQLite market is unavailable");
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO vel_orders (id, buyer, buyer_name, material, amount, filled,"
                    + " price_each, created, expires, closed, collected, total_exact) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    + " ON CONFLICT(id) DO UPDATE SET buyer=excluded.buyer, buyer_name=excluded.buyer_name,"
                    + " material=excluded.material, amount=excluded.amount, filled=excluded.filled,"
                    + " price_each=excluded.price_each, created=excluded.created, expires=excluded.expires,"
                    + " closed=excluded.closed, collected=excluded.collected, total_exact=excluded.total_exact")) {
                ps.setString(1, entry.id()); ps.setString(2, entry.buyer().toString());
                ps.setString(3, entry.buyerName()); ps.setString(4, entry.material());
                ps.setInt(5, entry.amount()); ps.setInt(6, entry.filled()); ps.setLong(7, entry.priceEachCents());
                ps.setLong(8, entry.created()); ps.setLong(9, entry.expires());
                ps.setInt(10, entry.closed() ? 1 : 0); ps.setInt(11, entry.collected());
                ps.setString(12, entry.totalCents().toString());
                ps.executeUpdate();
            } catch (SQLException ex) { throw new IllegalStateException("Could not persist buy order", ex); }
            buyOrders.put(entry.id(), entry);
        }
    }

    @Override
    public List<HomeLocation> homesFor(UUID owner) {
        List<HomeLocation> out = new ArrayList<>();
        for (HomeLocation home : homes.values()) {
            if (home.owner().equals(owner)) {
                out.add(home);
            }
        }
        out.sort(Comparator.comparing(HomeLocation::name));
        return out;
    }

    @Override
    public void saveHome(HomeLocation home) {
        homes.put(home.owner().toString() + ":" + home.name(), home);
        execute("INSERT INTO vel_homes (owner, name, world, x, y, z, yaw, pitch)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                        + " ON CONFLICT(owner, name) DO UPDATE SET world = excluded.world,"
                        + " x = excluded.x, y = excluded.y, z = excluded.z, yaw = excluded.yaw,"
                        + " pitch = excluded.pitch",
                home.owner().toString(), home.name(), home.world(), home.x(), home.y(), home.z(),
                (double) home.yaw(), (double) home.pitch());
    }

    @Override
    public void removeHome(UUID owner, String name) {
        homes.remove(owner.toString() + ":" + name);
        execute("DELETE FROM vel_homes WHERE owner = ? AND name = ?", owner.toString(), name);
    }

    @Override
    public String homeIcon(UUID owner, String name) {
        return homeIcons.get(owner.toString() + ":" + name);
    }

    @Override
    public void setHomeIcon(UUID owner, String name, String material) {
        String key = owner.toString() + ":" + name;
        if (material == null) {
            homeIcons.remove(key);
        } else {
            homeIcons.put(key, material);
        }
        execute("UPDATE vel_homes SET icon = ? WHERE owner = ? AND name = ?",
                material == null ? "" : material, owner.toString(), name);
    }

    @Override
    public long shards(UUID uuid) {
        return shards.getOrDefault(uuid, 0L);
    }

    @Override
    public void setShards(UUID uuid, long amount) {
        shards.put(uuid, amount);
        execute("INSERT INTO vel_shards (uuid, shards) VALUES (?, ?)"
                        + " ON CONFLICT(uuid) DO UPDATE SET shards = excluded.shards",
                uuid.toString(), amount);
    }

    @Override
    public java.util.Map<UUID, Long> shardBalances() {
        return new java.util.HashMap<>(shards);
    }

    @Override
    public long kills(UUID uuid) {
        long[] v = stats.get(uuid);
        return v == null ? 0L : v[0];
    }

    @Override
    public long deaths(UUID uuid) {
        long[] v = stats.get(uuid);
        return v == null ? 0L : v[1];
    }

    @Override
    public void setKills(UUID uuid, long amount) {
        long[] v = stats.computeIfAbsent(uuid, k -> new long[2]);
        v[0] = amount;
        execute("INSERT INTO vel_stats (uuid, kills, deaths) VALUES (?, ?, ?)"
                        + " ON CONFLICT(uuid) DO UPDATE SET kills = excluded.kills",
                uuid.toString(), amount, v[1]);
    }

    @Override
    public void setDeaths(UUID uuid, long amount) {
        long[] v = stats.computeIfAbsent(uuid, k -> new long[2]);
        v[1] = amount;
        execute("INSERT INTO vel_stats (uuid, kills, deaths) VALUES (?, ?, ?)"
                        + " ON CONFLICT(uuid) DO UPDATE SET deaths = excluded.deaths",
                uuid.toString(), v[0], amount);
    }

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
