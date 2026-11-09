package org.bukkit.event.player;

import org.bukkit.Location;
import org.bukkit.event.Cancellable;

public class PlayerMoveEvent extends PlayerEvent implements Cancellable {
    public Location getFrom() { return null; }
    public Location getTo() { return null; }
    public void setTo(Location to) {}
    @Override public void setCancelled(boolean cancelled) {}
    @Override public boolean isCancelled() { return false; }
}
