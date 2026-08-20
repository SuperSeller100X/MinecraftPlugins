package org.bukkit.event.player;

import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

/**
 * Compile-only stub — never shipped in the plugin jar.
 */
public class AsyncPlayerChatEvent extends PlayerEvent implements Cancellable {

    private String message;
    private boolean cancelled;

    public AsyncPlayerChatEvent(boolean async, Player who, String message, Set<Player> recipients) {
        super(who);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Set<Player> getRecipients() {
        return null;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
