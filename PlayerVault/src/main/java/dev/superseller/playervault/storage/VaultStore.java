package dev.superseller.playervault.storage;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import dev.superseller.playervault.model.VaultData;

/**
 * Persistence backend for vaults.
 *
 * <p>Implementations are called from asynchronous tasks and must therefore be safe
 * to use off the region thread. They must not touch Bukkit scheduler or world state.
 */
public interface VaultStore extends AutoCloseable {

    /** Human readable backend name, shown in {@code /pv reload} output and the log. */
    String name();

    /**
     * Loads a vault.
     *
     * @return the stored vault, or {@code null} when the player has no vault yet
     */
    VaultData load(UUID owner) throws IOException;

    /** Writes a vault, creating it when necessary. */
    void save(VaultData data) throws IOException;

    /** Deletes a vault. Missing vaults are not an error. */
    void delete(UUID owner) throws IOException;

    /** Every player that has a stored vault. */
    List<UUID> all() throws IOException;

    /** Flushes and releases resources. Never throws. */
    @Override
    void close();
}
