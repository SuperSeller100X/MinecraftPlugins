package dev.superseller.veltryx.punish;

import dev.superseller.veltryx.model.WarnEntry;
import dev.superseller.veltryx.storage.DataStore;
import dev.superseller.veltryx.util.Ids;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Staff warnings. Warnings expire after a configurable period; when a player
 * collects the configured number of active warnings the moderation commands
 * apply the configured escalation (kick, mute or ban).
 */
public final class WarnService {

    private final DataStore store;
    private final long expiryMillis;

    public WarnService(DataStore store, long expirySeconds) {
        this.store = store;
        this.expiryMillis = expirySeconds <= 0 ? 0L : expirySeconds * 1000L;
    }

    /** Issues a warning and returns the player's new active warning count. */
    public int warn(UUID uuid, String name, String reason, String staff) {
        WarnEntry entry = new WarnEntry(Ids.next(), uuid, name, reason, staff,
                System.currentTimeMillis(), true);
        store.saveWarn(entry);
        return activeWarns(uuid).size();
    }

    /** Active warnings of a player (not yet expired), newest first. */
    public List<WarnEntry> activeWarns(UUID uuid) {
        long cutoff = expiryMillis == 0L ? 0L : System.currentTimeMillis() - expiryMillis;
        return store.allWarns().stream()
                .filter(w -> w.uuid().equals(uuid) && w.active() && w.issued() >= cutoff)
                .sorted(Comparator.comparingLong(WarnEntry::issued).reversed())
                .toList();
    }

    /** Every warning on record for a player, newest first. */
    public List<WarnEntry> history(UUID uuid) {
        return store.allWarns().stream()
                .filter(w -> w.uuid().equals(uuid))
                .sorted(Comparator.comparingLong(WarnEntry::issued).reversed())
                .toList();
    }

    /** Clears the active warnings of a player. */
    public int clear(UUID uuid) {
        int cleared = 0;
        for (WarnEntry entry : activeWarns(uuid)) {
            store.setWarnActive(entry.id(), false);
            cleared++;
        }
        return cleared;
    }
}
