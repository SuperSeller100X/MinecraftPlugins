package dev.superseller.xpbank.listener;

import dev.superseller.xpbank.storage.BankStorage;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Keeps the UUID to display-name mapping current so the leaderboard and admin
 * commands can show real names even for players who have changed theirs.
 */
public final class PlayerJoinListener implements Listener {

    private final BankStorage storage;

    public PlayerJoinListener(BankStorage storage) {
        this.storage = storage;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        storage.rememberName(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }
}
