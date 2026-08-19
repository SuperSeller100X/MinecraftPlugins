package dev.superseller.gifty.listener;

import dev.superseller.gifty.input.ChatInputManager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Captures chat while a gift prompt (money/message) is pending. The chat
 * event fires async; the input manager defers completion to the player's
 * owning thread.
 */
public final class ChatListener implements Listener {

    private final ChatInputManager chatInput;

    public ChatListener(ChatInputManager chatInput) {
        this.chatInput = chatInput;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!chatInput.hasPending(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        chatInput.onChatAsync(player, event.getMessage());
    }
}
