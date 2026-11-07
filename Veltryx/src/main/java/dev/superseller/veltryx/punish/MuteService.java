package dev.superseller.veltryx.punish;

import dev.superseller.veltryx.model.MuteEntry;
import dev.superseller.veltryx.storage.DataStore;
import dev.superseller.veltryx.util.Ids;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Chat mutes: issued with {@code /mute}, enforced by the chat listener. */
public final class MuteService {

    private final DataStore store;

    public MuteService(DataStore store) {
        this.store = store;
    }

    /** Issues a mute. */
    public MuteEntry mute(UUID uuid, String name, String reason, String staff, long expiresMillis) {
        MuteEntry entry = new MuteEntry(Ids.next(), uuid, name, reason, staff,
                System.currentTimeMillis(), expiresMillis, true);
        store.saveMute(entry);
        return entry;
    }

    /** Lifts the active mute of a player, if any. */
    public Optional<MuteEntry> unmute(UUID uuid) {
        Optional<MuteEntry> active = activeMute(uuid);
        active.ifPresent(entry -> store.setMuteActive(entry.id(), false));
        return active;
    }

    /** The mute currently in force for a player, if any. */
    public Optional<MuteEntry> activeMute(UUID uuid) {
        long now = System.currentTimeMillis();
        return store.allMutes().stream()
                .filter(m -> m.uuid().equals(uuid) && m.isActiveAt(now))
                .max(Comparator.comparingLong(MuteEntry::issued));
    }

    /** Every mute on record for a player, newest first. */
    public List<MuteEntry> history(UUID uuid) {
        return store.allMutes().stream()
                .filter(m -> m.uuid().equals(uuid))
                .sorted(Comparator.comparingLong(MuteEntry::issued).reversed())
                .toList();
    }
}
