package org.bukkit.event.player;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
public class PlayerItemHeldEvent extends Event {
    public Player getPlayer() { return null; }
    public int getPreviousSlot() { return -1; }
    public int getNewSlot() { return -1; }
}
