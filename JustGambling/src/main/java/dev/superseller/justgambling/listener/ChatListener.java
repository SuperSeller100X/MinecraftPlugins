package dev.superseller.justgambling.listener;

import dev.superseller.justgambling.input.InputManager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/** Captures chat only while a player is entering a custom wager. */
public final class ChatListener implements Listener {
    private final InputManager input;

    public ChatListener(InputManager input) {
        this.input = input;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!input.hasPending(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        input.onChatAsync(player, event.getMessage());
    }
}
