package dev.superseller.chunkvoter.listener;

import dev.superseller.chunkvoter.ChunkVoterPlugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Cancels a vote if the player who started it logs out (configurable via
 * {@code vote.auto-cancel-on-quit}).
 */
public final class PlayerQuitListener implements Listener {

    private final ChunkVoterPlugin plugin;

    public PlayerQuitListener(ChunkVoterPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.configuration().autoCancelOnQuit()) {
            plugin.voteManager().cancelForInitiator(event.getPlayer().getUniqueId());
        }
    }
}
