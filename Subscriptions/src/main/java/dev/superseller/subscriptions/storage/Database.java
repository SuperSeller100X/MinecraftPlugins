package dev.superseller.subscriptions.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import dev.superseller.subscriptions.model.ChargePolicy;
import dev.superseller.subscriptions.model.InboxEntry;
import dev.superseller.subscriptions.model.Plan;
import dev.superseller.subscriptions.model.PlanStatus;
import dev.superseller.subscriptions.model.Subscription;
import dev.superseller.subscriptions.model.SubscriptionStatus;

/**
 * SQLite storage. One file ({@code subscriptions.db}) that works on Linux,
 * Windows and macOS. In-memory maps are the source of truth at runtime;
 * every mutation is written through immediately.
 */
public final class Database {

    private final File file;
    private final Logger logger;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Plan> plans = new ConcurrentHashMap<>();
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    private Connection connection;

    public Database(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void open() {
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
                        CREATE TABLE IF NOT EXISTS plans (
                          id TEXT PRIMARY KEY,
                          owner_uuid TEXT,
                          owner_name TEXT,
                          name TEXT,
                          description TEXT,
                          category TEXT,
                          price REAL,
                          signup_fee REAL,
                          interval_ms INTEGER,
                          policy TEXT,
                          max_subscribers INTEGER,
                          max_cycles INTEGER,
                          trial_cycles INTEGER,
                          infinite_stock INTEGER,
                          stock_kits INTEGER,
                          status TEXT,
                          created_at INTEGER,
                          rewards TEXT,
                          icon TEXT
                        )""");
                s.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS subscriptions (
                          id TEXT PRIMARY KEY,
                          plan_id TEXT,
                          subscriber_uuid TEXT,
                          subscriber_name TEXT,
                          status TEXT,
                          policy TEXT,
                          cycles INTEGER,
                          next_charge_at INTEGER,
                          created_at INTEGER,
                          cancelled_at INTEGER,
                          cancel_reason TEXT,
                          auto_renew INTEGER
                        )""");
                s.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS inbox (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          owner_uuid TEXT,
                          item_b64 TEXT,
                          source TEXT,
                          created_at INTEGER
                        )""");
                s.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS charges (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          subscription_id TEXT,
                          plan_id TEXT,
                          subscriber_uuid TEXT,
                          seller_uuid TEXT,
                          amount REAL,
                          tax REAL,
                          result TEXT,
                          detail TEXT,
                          created_at INTEGER
                        )""");
            }
            loadAll();
            recountSubscribers();
            logger.info("SQLite ready (" + plans.size() + " plans, "
                    + subscriptions.size() + " subscriptions) at " + file.getAbsolutePath());
        } catch (Exception e) {
            logger.severe("Could not open SQLite database: " + e.getMessage());
        }
    }

    public void close() {
        lock.lock();
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException ignored) {
            // already closing
        } finally {
            connection = null;
            lock.unlock();
        }
    }

    public Collection<Plan> plans() {
        return plans.values();
    }

    public Plan plan(String id) {
        return id == null ? null : plans.get(id);
    }

    public Subscription subscription(String id) {
        return id == null ? null : subscriptions.get(id);
    }

    public Collection<Subscription> subscriptions() {
        return subscriptions.values();
    }

    public List<Plan> plansByOwner(UUID owner) {
        List<Plan> out = new ArrayList<>();
        for (Plan plan : plans.values()) {
            if (owner == null ? plan.serverOwned() : plan.ownedBy(owner)) {
                out.add(plan);
            }
        }
        out.sort((a, b) -> Long.compare(b.createdAt(), a.createdAt()));
        return out;
    }

    public List<Plan> listedPlans(String query, String category) {
        String q = query == null ? "" : query.toLowerCase();
        String cat = category == null || category.equalsIgnoreCase("ALL") ? "" : category.toUpperCase();
        List<Plan> out = new ArrayList<>();
        for (Plan plan : plans.values()) {
            if (!plan.status().listed()) {
                continue;
            }
            if (!cat.isEmpty() && !plan.category().equals(cat)) {
                continue;
            }
            if (!q.isEmpty() && !plan.name().toLowerCase().contains(q)
                    && !plan.id().toLowerCase().contains(q)
                    && !plan.ownerName().toLowerCase().contains(q)
                    && !plan.description().toLowerCase().contains(q)) {
                continue;
            }
            out.add(plan);
        }
        out.sort((a, b) -> Integer.compare(b.subscriberCount(), a.subscriberCount()));
        return out;
    }

    public List<Subscription> subscriptionsOf(UUID player) {
        List<Subscription> out = new ArrayList<>();
        for (Subscription sub : subscriptions.values()) {
            if (sub.subscriberId().equals(player)) {
                out.add(sub);
            }
        }
        out.sort((a, b) -> Long.compare(b.createdAt(), a.createdAt()));
        return out;
    }

    public Subscription activeOn(UUID player, String planId) {
        for (Subscription sub : subscriptions.values()) {
            if (sub.subscriberId().equals(player) && sub.planId().equals(planId) && sub.status().living()) {
                return sub;
            }
        }
        return null;
    }

    public List<Subscription> due(long now) {
        List<Subscription> out = new ArrayList<>();
        for (Subscription sub : subscriptions.values()) {
            if (sub.status().billable() && sub.autoRenew() && sub.nextChargeAt() <= now) {
                out.add(sub);
            }
        }
        return out;
    }

    public void savePlan(Plan plan) {
        plans.put(plan.id(), plan);
        lock.lock();
        try {
            if (connection == null) {
                return;
            }
            try (PreparedStatement s = connection.prepareStatement("""
                    INSERT INTO plans(id,owner_uuid,owner_name,name,description,category,price,signup_fee,interval_ms,policy,
                      max_subscribers,max_cycles,trial_cycles,infinite_stock,stock_kits,status,created_at,rewards,icon)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    ON CONFLICT(id) DO UPDATE SET
                      owner_uuid=excluded.owner_uuid, owner_name=excluded.owner_name, name=excluded.name,
                      description=excluded.description, category=excluded.category, price=excluded.price,
                      signup_fee=excluded.signup_fee, interval_ms=excluded.interval_ms, policy=excluded.policy,
                      max_subscribers=excluded.max_subscribers, max_cycles=excluded.max_cycles,
                      trial_cycles=excluded.trial_cycles, infinite_stock=excluded.infinite_stock,
                      stock_kits=excluded.stock_kits, status=excluded.status, rewards=excluded.rewards, icon=excluded.icon
                    """)) {
                s.setString(1, plan.id());
                s.setString(2, plan.ownerId() == null ? null : plan.ownerId().toString());
                s.setString(3, plan.ownerName());
                s.setString(4, plan.name());
                s.setString(5, plan.description());
                s.setString(6, plan.category());
                s.setDouble(7, plan.price());
                s.setDouble(8, plan.signupFee());
                s.setLong(9, plan.intervalMs());
                s.setString(10, plan.policy().name());
                s.setInt(11, plan.maxSubscribers());
                s.setInt(12, plan.maxCycles());
                s.setInt(13, plan.trialCycles());
                s.setInt(14, plan.infiniteStock() ? 1 : 0);
                s.setInt(15, plan.stockKits());
                s.setString(16, plan.status().name());
                s.setLong(17, plan.createdAt());
                s.setString(18, plan.encodeRewards());
                s.setString(19, plan.icon());
                s.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Could not save plan " + plan.id() + ": " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public void deletePlan(String id) {
        plans.remove(id);
        lock.lock();
        try {
            if (connection == null) {
                return;
            }
            try (PreparedStatement s = connection.prepareStatement("DELETE FROM plans WHERE id=?")) {
                s.setString(1, id);
                s.executeUpdate();
            }
        } catch (SQLException e) {
            logger.warning("Could not delete plan " + id + ": " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public void saveSubscription(Subscription sub) {
        subscriptions.put(sub.id(), sub);
        lock.lock();
        try {
            if (connection == null) {
                return;
            }
            try (PreparedStatement s = connection.prepareStatement("""
                    INSERT INTO subscriptions(id,plan_id,subscriber_uuid,subscriber_name,status,policy,cycles,
                      next_charge_at,created_at,cancelled_at,cancel_reason,auto_renew)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                    ON CONFLICT(id) DO UPDATE SET
                      status=excluded.status, policy=excluded.policy, cycles=excluded.cycles,
                      next_charge_at=excluded.next_charge_at, cancelled_at=excluded.cancelled_at,
                      cancel_reason=excluded.cancel_reason, auto_renew=excluded.auto_renew,
                      subscriber_name=excluded.subscriber_name
                    """)) {
                s.setString(1, sub.id());
                s.setString(2, sub.planId());
                s.setString(3, sub.subscriberId().toString());
                s.setString(4, sub.subscriberName());
                s.setString(5, sub.status().name());
                s.setString(6, sub.policyOverride() == null ? null : sub.policyOverride().name());
                s.setInt(7, sub.cycles());
                s.setLong(8, sub.nextChargeAt());
                s.setLong(9, sub.createdAt());
                s.setLong(10, sub.cancelledAt());
                s.setString(11, sub.cancelReason());
                s.setInt(12, sub.autoRenew() ? 1 : 0);
                s.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Could not save subscription " + sub.id() + ": " + e.getMessage());
        } finally {
            lock.unlock();
        }
        recountSubscribers();
    }

    public long addInbox(UUID owner, String itemBase64, String source) {
        lock.lock();
        try {
            if (connection == null) {
                return -1L;
            }
            try (PreparedStatement s = connection.prepareStatement(
                    "INSERT INTO inbox(owner_uuid,item_b64,source,created_at) VALUES(?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                s.setString(1, owner.toString());
                s.setString(2, itemBase64);
                s.setString(3, source);
                s.setLong(4, System.currentTimeMillis());
                s.executeUpdate();
                try (ResultSet keys = s.getGeneratedKeys()) {
                    return keys.next() ? keys.getLong(1) : -1L;
                }
            }
        } catch (SQLException e) {
            logger.severe("Could not write inbox: " + e.getMessage());
            return -1L;
        } finally {
            lock.unlock();
        }
    }

    public List<InboxEntry> inbox(UUID owner) {
        List<InboxEntry> out = new ArrayList<>();
        lock.lock();
        try {
            if (connection == null) {
                return out;
            }
            try (PreparedStatement s = connection.prepareStatement(
                    "SELECT id,owner_uuid,item_b64,source,created_at FROM inbox WHERE owner_uuid=? ORDER BY id ASC")) {
                s.setString(1, owner.toString());
                try (ResultSet r = s.executeQuery()) {
                    while (r.next()) {
                        out.add(new InboxEntry(
                                r.getLong("id"),
                                UUID.fromString(r.getString("owner_uuid")),
                                r.getString("item_b64"),
                                r.getString("source"),
                                r.getLong("created_at")));
                    }
                }
            }
        } catch (SQLException e) {
            logger.warning("Could not read inbox: " + e.getMessage());
        } finally {
            lock.unlock();
        }
        return out;
    }

    public int inboxSize(UUID owner) {
        lock.lock();
        try {
            if (connection == null) {
                return 0;
            }
            try (PreparedStatement s = connection.prepareStatement("SELECT COUNT(*) FROM inbox WHERE owner_uuid=?")) {
                s.setString(1, owner.toString());
                try (ResultSet r = s.executeQuery()) {
                    return r.next() ? r.getInt(1) : 0;
                }
            }
        } catch (SQLException e) {
            return 0;
        } finally {
            lock.unlock();
        }
    }

    public boolean deleteInbox(long id, UUID owner) {
        lock.lock();
        try {
            if (connection == null) {
                return false;
            }
            try (PreparedStatement s = connection.prepareStatement("DELETE FROM inbox WHERE id=? AND owner_uuid=?")) {
                s.setLong(1, id);
                s.setString(2, owner.toString());
                return s.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            return false;
        } finally {
            lock.unlock();
        }
    }

    public void logCharge(String subId, String planId, UUID subscriber, UUID seller,
                          double amount, double tax, String result, String detail) {
        lock.lock();
        try {
            if (connection == null) {
                return;
            }
            try (PreparedStatement s = connection.prepareStatement("""
                    INSERT INTO charges(subscription_id,plan_id,subscriber_uuid,seller_uuid,amount,tax,result,detail,created_at)
                    VALUES(?,?,?,?,?,?,?,?,?)
                    """)) {
                s.setString(1, subId);
                s.setString(2, planId);
                s.setString(3, subscriber == null ? null : subscriber.toString());
                s.setString(4, seller == null ? null : seller.toString());
                s.setDouble(5, amount);
                s.setDouble(6, tax);
                s.setString(7, result);
                s.setString(8, detail);
                s.setLong(9, System.currentTimeMillis());
                s.executeUpdate();
            }
        } catch (SQLException e) {
            logger.warning("Could not log charge: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public List<String> history(UUID player, int limit) {
        List<String> out = new ArrayList<>();
        lock.lock();
        try {
            if (connection == null) {
                return out;
            }
            try (PreparedStatement s = connection.prepareStatement("""
                    SELECT created_at, result, amount, detail, plan_id FROM charges
                    WHERE subscriber_uuid=? OR seller_uuid=?
                    ORDER BY id DESC LIMIT ?
                    """)) {
                s.setString(1, player.toString());
                s.setString(2, player.toString());
                s.setInt(3, Math.max(1, limit));
                try (ResultSet r = s.executeQuery()) {
                    while (r.next()) {
                        out.add(r.getLong("created_at") + "|" + r.getString("result") + "|"
                                + r.getDouble("amount") + "|" + r.getString("plan_id") + "|"
                                + nullToEmpty(r.getString("detail")));
                    }
                }
            }
        } catch (SQLException e) {
            logger.warning("Could not read history: " + e.getMessage());
        } finally {
            lock.unlock();
        }
        return out;
    }

    public int planCount() {
        return plans.size();
    }

    public int liveSubscriptionCount() {
        int n = 0;
        for (Subscription sub : subscriptions.values()) {
            if (sub.status().living()) {
                n++;
            }
        }
        return n;
    }

    public int totalInbox() {
        lock.lock();
        try {
            if (connection == null) {
                return 0;
            }
            try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM inbox")) {
                return r.next() ? r.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        } finally {
            lock.unlock();
        }
    }

    public double spent(UUID player) {
        return sum("SELECT COALESCE(SUM(amount),0) FROM charges WHERE subscriber_uuid=? AND result='CHARGE'", player);
    }

    public double earned(UUID player) {
        return sum("SELECT COALESCE(SUM(amount-tax),0) FROM charges WHERE seller_uuid=? AND result='CHARGE'", player);
    }

    public void pruneCharges(int days) {
        if (days <= 0) {
            return;
        }
        long cutoff = System.currentTimeMillis() - days * 86_400_000L;
        lock.lock();
        try {
            if (connection == null) {
                return;
            }
            try (PreparedStatement s = connection.prepareStatement("DELETE FROM charges WHERE created_at < ?")) {
                s.setLong(1, cutoff);
                s.executeUpdate();
            }
        } catch (SQLException e) {
            logger.warning("Could not prune charge logs: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private double sum(String sql, UUID player) {
        lock.lock();
        try {
            if (connection == null) {
                return 0d;
            }
            try (PreparedStatement s = connection.prepareStatement(sql)) {
                s.setString(1, player.toString());
                try (ResultSet r = s.executeQuery()) {
                    return r.next() ? r.getDouble(1) : 0d;
                }
            }
        } catch (SQLException e) {
            return 0d;
        } finally {
            lock.unlock();
        }
    }

    private void loadAll() throws SQLException {
        plans.clear();
        subscriptions.clear();
        if (connection == null) {
            return;
        }
        try (Statement s = connection.createStatement();
             ResultSet r = s.executeQuery("SELECT * FROM plans")) {
            while (r.next()) {
                Plan plan = new Plan(r.getString("id"));
                String owner = r.getString("owner_uuid");
                if (owner != null && !owner.isBlank()) {
                    try {
                        plan.ownerId(UUID.fromString(owner));
                    } catch (IllegalArgumentException ignored) {
                        // leave server-owned
                    }
                }
                plan.ownerName(r.getString("owner_name"));
                plan.name(r.getString("name"));
                plan.description(r.getString("description"));
                plan.category(r.getString("category"));
                plan.price(r.getDouble("price"));
                plan.signupFee(r.getDouble("signup_fee"));
                plan.intervalMs(r.getLong("interval_ms"));
                plan.policy(ChargePolicy.fromString(r.getString("policy"), ChargePolicy.PAUSE));
                plan.maxSubscribers(r.getInt("max_subscribers"));
                plan.maxCycles(r.getInt("max_cycles"));
                plan.trialCycles(r.getInt("trial_cycles"));
                plan.infiniteStock(r.getInt("infinite_stock") != 0);
                plan.stockKits(r.getInt("stock_kits"));
                plan.status(PlanStatus.fromString(r.getString("status"), PlanStatus.DRAFT));
                plan.createdAt(r.getLong("created_at"));
                plan.decodeRewards(r.getString("rewards"));
                plan.icon(r.getString("icon"));
                plans.put(plan.id(), plan);
            }
        }
        try (Statement s = connection.createStatement();
             ResultSet r = s.executeQuery("SELECT * FROM subscriptions")) {
            while (r.next()) {
                UUID subscriber;
                try {
                    subscriber = UUID.fromString(r.getString("subscriber_uuid"));
                } catch (RuntimeException e) {
                    continue;
                }
                Subscription sub = new Subscription(r.getString("id"), r.getString("plan_id"), subscriber);
                sub.subscriberName(r.getString("subscriber_name"));
                sub.status(SubscriptionStatus.fromString(r.getString("status"), SubscriptionStatus.ACTIVE));
                String policy = r.getString("policy");
                if (policy != null && !policy.isBlank()) {
                    sub.policyOverride(ChargePolicy.fromString(policy, null));
                }
                sub.cycles(r.getInt("cycles"));
                sub.nextChargeAt(r.getLong("next_charge_at"));
                sub.createdAt(r.getLong("created_at"));
                sub.cancelledAt(r.getLong("cancelled_at"));
                sub.cancelReason(r.getString("cancel_reason"));
                sub.autoRenew(r.getInt("auto_renew") != 0);
                subscriptions.put(sub.id(), sub);
            }
        }
    }

    public void recountSubscribers() {
        Map<String, Integer> counts = new ConcurrentHashMap<>();
        for (Subscription sub : subscriptions.values()) {
            if (sub.status().living()) {
                counts.merge(sub.planId(), 1, Integer::sum);
            }
        }
        for (Plan plan : plans.values()) {
            plan.subscriberCount(counts.getOrDefault(plan.id(), 0));
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
