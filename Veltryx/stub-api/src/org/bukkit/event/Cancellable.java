package org.bukkit.event;
public interface Cancellable {
    void setCancelled(boolean cancelled);
    boolean isCancelled();
}
