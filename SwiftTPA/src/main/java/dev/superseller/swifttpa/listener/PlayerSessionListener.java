package dev.superseller.swifttpa.listener;

import dev.superseller.swifttpa.SwiftTPAPlugin;
import dev.superseller.swifttpa.scheduler.PlatformScheduler;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Loads a player's stored settings shortly after join (asynchronously, so the
 * login tick never touches the disk) and flushes them on quit. Quitting also
 * clears every request involving the player and warns the other side.
 */
public final class PlayerSessionListener implements Listener {

    private final SwiftTPAPlugin plugin;

    public PlayerSessionListener(SwiftTPAPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PlatformScheduler.runAsync(() -> plugin.storage().loadPlayer(uuid));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        plugin.service().onPlayerQuit(uuid, player.getName());
        PlatformScheduler.runAsync(() -> plugin.storage().savePlayer(uuid));
    }
}
