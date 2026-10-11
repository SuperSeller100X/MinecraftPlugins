package dev.superseller.homesgui.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import dev.superseller.homesgui.HomesGUIPlugin;

/** Flushes profile changes after a player leaves when configured to do so. */
public final class ConnectionListener implements Listener {

    private final HomesGUIPlugin plugin;

    public ConnectionListener(HomesGUIPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.settings().storage().saveOnQuit()) {
            plugin.storage().saveIfDirty();
        }
    }
}
