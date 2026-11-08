package dev.superseller.veltryx.stats;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Feeds the stats service: playtime sessions on join/quit and death/kill
 * counting on every player death.
 */
public final class StatsListener implements Listener {

    private final StatsService stats;
    private final Runnable onChange;

    /**
     * @param onChange runs after a stat change (the scoreboard service
     *                 refreshes the visible lines through it)
     */
    public StatsListener(StatsService stats, Runnable onChange) {
        this.stats = stats;
        this.onChange = onChange;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        stats.sessionStart(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stats.sessionEnd(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player killer = victim.getKiller();
        stats.recordDeath(victim, killer);
        onChange.run();
    }
}
