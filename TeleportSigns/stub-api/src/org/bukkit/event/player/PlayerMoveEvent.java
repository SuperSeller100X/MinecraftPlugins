package org.bukkit.event.player;
import org.bukkit.Location;
import org.bukkit.event.Cancellable;
public class PlayerMoveEvent extends PlayerEvent implements Cancellable {
    public Location getTo() { return null; }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) {}
}
