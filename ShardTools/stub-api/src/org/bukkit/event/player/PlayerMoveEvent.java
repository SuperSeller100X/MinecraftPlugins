package org.bukkit.event.player;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
public class PlayerMoveEvent extends Event {
    public Player getPlayer() { return null; }
    public Location getFrom() { return null; }
    public Location getTo() { return null; }
    public void setTo(Location to) {}
    public void setCancelled(boolean cancelled) {}
}
