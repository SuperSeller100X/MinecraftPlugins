package org.bukkit.event.player;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
public class PlayerItemConsumeEvent extends Event {
    public Player getPlayer() { return null; }
    public ItemStack getItem() { return null; }
}
