package dev.superseller.gifty.listener;

import dev.superseller.gifty.GuiSessionManager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Notifies players about pending gifts on join and protects in-progress
 * compose sessions on quit (items are queued for return).
 */
public final class JoinQuitListener implements Listener {

    private final GuiSessionManager sessions;

    public JoinQuitListener(GuiSessionManager sessions) {
        this.sessions = sessions;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // slight delay so other plugins' join handlers don't overlap
        dev.superseller.gifty.scheduler.PlatformScheduler.runEntitySync(player,
                () -> sessions.onJoin(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        sessions.onQuit(event.getPlayer());
    }
}
