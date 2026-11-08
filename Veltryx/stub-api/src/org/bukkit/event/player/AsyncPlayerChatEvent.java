package org.bukkit.event.player;

import org.bukkit.event.Cancellable;

public class AsyncPlayerChatEvent extends PlayerEvent implements Cancellable {
    @Override public void setCancelled(boolean cancelled) {}
    @Override public boolean isCancelled() { return false; }
    public void setFormat(String format) {}
    public String getMessage() { return ""; }
    public void setMessage(String message) {}
}
