package dev.superseller.veltryx.stats;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import dev.superseller.veltryx.model.Profile;
import dev.superseller.veltryx.storage.DataStore;

/**
 * Kill/death counters and playtime for the scoreboard and nametag. Kills
 * are PVP kills (a player landed the final hit); deaths count every death.
 * Playtime is the stored playtime plus the running session.
 */
public final class StatsService {

    private final DataStore store;
    /** Session start millis per online player (for live playtime). */
    private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<>();

    public StatsService(DataStore store) {
        this.store = store;
    }

    /** Starts the live playtime session of a player. */
    public void sessionStart(Player player) {
        sessionStart.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /** Ends the session and folds it into the stored playtime. */
    public void sessionEnd(Player player) {
        Long since = sessionStart.remove(player.getUniqueId());
        if (since == null) {
            return;
        }
        Profile profile = store.getProfile(player.getUniqueId());
        long played = profile == null ? 0L : profile.playtimeMs();
        store.upsertProfile(player.getUniqueId(), player.getName(),
                System.currentTimeMillis(), played + (System.currentTimeMillis() - since), false);
    }

    /** Playtime of a player including the running session, in millis. */
    public long playtimeMillis(UUID id) {
        Profile profile = store.getProfile(id);
        long played = profile == null ? 0L : profile.playtimeMs();
        Long since = sessionStart.get(id);
        return played + (since == null ? 0L : System.currentTimeMillis() - since);
    }

    /** Records a death (and the killer's PVP kill when there is one). */
    public void recordDeath(Player victim, Player killer) {
        store.setDeaths(victim.getUniqueId(), store.deaths(victim.getUniqueId()) + 1);
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            store.setKills(killer.getUniqueId(), store.kills(killer.getUniqueId()) + 1);
        }
    }

    public long kills(UUID id) {
        return store.kills(id);
    }

    public long deaths(UUID id) {
        return store.deaths(id);
    }
}
