package dev.superseller.swifttpa.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent per-player settings and statistics: the request toggle, the
 * teleport block list (uuid plus last known name for display and
 * tab-completion) and lifetime counters. Mutations are synchronized so the
 * object is safe to touch from Folia's region threads.
 */
public final class PlayerData {

    private final UUID uuid;
    private boolean requestsEnabled = true;
    private final Map<UUID, String> blocked = new LinkedHashMap<>();

    private long sentCount;
    private long acceptedCount;
    private long deniedCount;
    private long teleportedCount;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID uuid() {
        return uuid;
    }

    public synchronized boolean requestsEnabled() {
        return requestsEnabled;
    }

    public synchronized void setRequestsEnabled(boolean enabled) {
        this.requestsEnabled = enabled;
    }

    public synchronized boolean isBlocked(UUID other) {
        return blocked.containsKey(other);
    }

    /** Blocks a player; refreshes the stored name when already blocked. Returns true when newly added. */
    public synchronized boolean block(UUID other, String name) {
        String previous = blocked.put(other, name == null ? "?" : name);
        return previous == null;
    }

    /**
     * Removes a block entry matched by (case-insensitive) name or full uuid
     * string. Returns the removed entry's stored name, or null.
     */
    public synchronized String unblock(String nameOrUuid) {
        if (nameOrUuid == null) {
            return null;
        }
        for (Map.Entry<UUID, String> entry : new ArrayList<>(blocked.entrySet())) {
            boolean nameMatch = entry.getValue().equalsIgnoreCase(nameOrUuid);
            boolean uuidMatch = entry.getKey().toString().equalsIgnoreCase(nameOrUuid);
            if (nameMatch || uuidMatch) {
                blocked.remove(entry.getKey());
                return entry.getValue();
            }
        }
        return null;
    }

    /** Stored display names of every blocked player, insertion order. */
    public synchronized List<String> blockedNames() {
        return new ArrayList<>(blocked.values());
    }

    /** Raw "uuid;name" entries, the on-disk serialization format. */
    public synchronized List<String> serializeBlocked() {
        List<String> list = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : blocked.entrySet()) {
            list.add(entry.getKey() + ";" + entry.getValue());
        }
        return list;
    }

    /** Restores entries produced by {@link #serializeBlocked()}. */
    public synchronized void loadBlocked(List<String> entries) {
        blocked.clear();
        if (entries == null) {
            return;
        }
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            int sep = entry.indexOf(';');
            if (sep <= 0) {
                continue;
            }
            try {
                UUID id = UUID.fromString(entry.substring(0, sep).trim());
                String name = entry.substring(sep + 1).trim();
                blocked.put(id, name.isEmpty() ? "?" : name);
            } catch (IllegalArgumentException ignored) {
                // Skip malformed entries rather than failing the whole load.
            }
        }
    }

    // ------------------------------------------------------------- statistics

    public synchronized long sentCount() {
        return sentCount;
    }

    public synchronized long acceptedCount() {
        return acceptedCount;
    }

    public synchronized long deniedCount() {
        return deniedCount;
    }

    public synchronized long teleportedCount() {
        return teleportedCount;
    }

    public synchronized void addSent() {
        sentCount++;
    }

    public synchronized void addAccepted() {
        acceptedCount++;
    }

    public synchronized void addDenied() {
        deniedCount++;
    }

    public synchronized void addTeleported() {
        teleportedCount++;
    }

    public synchronized void setStats(long sent, long accepted, long denied, long teleported) {
        this.sentCount = Math.max(0L, sent);
        this.acceptedCount = Math.max(0L, accepted);
        this.deniedCount = Math.max(0L, denied);
        this.teleportedCount = Math.max(0L, teleported);
    }
}
