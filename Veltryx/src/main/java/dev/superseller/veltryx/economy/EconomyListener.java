package dev.superseller.veltryx.economy;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.storage.DataStore;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Player lifecycle: profile tracking (first-seen, last-seen, playtime) and the
 * starting balance for brand-new players.
 */
public final class EconomyListener implements Listener {

    private final DataStore store;
    private final EconomyService economy;
    private final Messages messages;
    private final Map<UUID, Long> joinTimes = new ConcurrentHashMap<>();

    public EconomyListener(DataStore store, EconomyService economy, Messages messages) {
        this.store = store;
        this.economy = economy;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        long now = System.currentTimeMillis();
        boolean firstJoin = store.getProfile(uuid) == null && store.getBalanceExact(uuid).signum() == 0;
        joinTimes.put(uuid, now);
        store.upsertProfile(uuid, name, now, 0L, true);
        if (firstJoin) {
            java.math.BigInteger granted = economy.grantStartingBalanceExact(uuid, name);
            if (granted.signum() > 0) {
                messages.send(player, "economy.starting-balance",
                        Map.of("amount", economy.format(granted)));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Long joinedAt = joinTimes.remove(uuid);
        long now = System.currentTimeMillis();
        long sessionMillis = joinedAt == null ? 0L : Math.max(0L, now - joinedAt);
        store.upsertProfile(uuid, event.getPlayer().getName(), now, sessionMillis, false);
    }
}
