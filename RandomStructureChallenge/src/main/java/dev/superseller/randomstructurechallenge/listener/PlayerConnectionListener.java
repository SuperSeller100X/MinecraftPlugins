package dev.superseller.randomstructurechallenge.listener;

import dev.superseller.randomstructurechallenge.RandomStructureChallengePlugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Show the BossBar to late joiners; drop await-state on disconnect.
 */
public final class PlayerConnectionListener implements Listener {

    private final RandomStructureChallengePlugin plugin;

    public PlayerConnectionListener(RandomStructureChallengePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.manager().handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.manager().handleQuit(event.getPlayer());
    }
}
