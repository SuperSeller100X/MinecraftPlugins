package dev.superseller.swifttpa.storage;

import java.util.UUID;

/**
 * Backend for per-player data. Implementations keep an in-memory cache that is
 * the runtime source of truth; {@link #loadPlayer(UUID)},
 * {@link #savePlayer(UUID)} and {@link #saveAll()} perform blocking I/O and
 * must be called from an asynchronous context (the plugin dispatches them via
 * {@code PlatformScheduler.runAsync}).
 */
public interface PlayerDataStorage {

    /** Opens the backend (creates folders / tables). Called once on enable. */
    void init();

    /** Returns the cached (or fresh default) data for a player. Never null. */
    PlayerData data(UUID uuid);

    /** Blocking read of one player from disk into the cache. Call async. */
    void loadPlayer(UUID uuid);

    /** Blocking write of one player's cached data to disk. Call async. */
    void savePlayer(UUID uuid);

    /** Blocking write of every cached player. Call async or on disable. */
    void saveAll();

    /** Flushes and closes the backend. Called on disable. */
    void close();
}
