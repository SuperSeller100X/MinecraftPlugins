package org.bukkit.event.player;

import org.bukkit.event.Cancellable;

public class PlayerItemConsumeEvent extends PlayerEvent implements Cancellable {
    @Override public void setCancelled(boolean cancelled) {}
    @Override public boolean isCancelled() { return false; }
}
