package dev.superseller.subscriptions.listener;

import dev.superseller.subscriptions.input.ChatInput;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ChatListener implements Listener {

    private final ChatInput chat;

    public ChatListener(ChatInput chat) {
        this.chat = chat;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (!chat.has(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        chat.onChatAsync(event.getPlayer(), message);
    }
}
