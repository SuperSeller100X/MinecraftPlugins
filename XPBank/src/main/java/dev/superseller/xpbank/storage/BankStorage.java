package dev.superseller.xpbank.storage;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistence abstraction for XP bank balances. Implementations must be safe to
 * call from asynchronous threads. An in-memory cache is the source of truth at
 * runtime; every mutation is written through to disk / database.
 */
public interface BankStorage {

    /** Loads all balances into memory. */
    void load();

    /** Flushes any pending state to persistent storage. */
    void save();

    /** Closes underlying resources (database connections, etc.). */
    void close();

    /** Current stored balance for a player (0 if none). */
    long get(UUID uuid);

    /** Overwrites the stored balance and remembers the player's last-known name. */
    void set(UUID uuid, String name, long amount);

    /** Records a display name for a UUID (used by the leaderboard). */
    void rememberName(UUID uuid, String name);

    /** Best-known display name for a UUID, or null. */
    String nameOf(UUID uuid);

    /** Every stored balance keyed by UUID (snapshot copy). */
    Map<UUID, Long> all();

    /** Top {@code limit} accounts by balance, highest first. */
    List<Entry> top(int limit);

    /** Sum of every stored balance. */
    long totalStored();

    /** Immutable leaderboard row. */
    record Entry(UUID uuid, String name, long amount) {
    }
}
