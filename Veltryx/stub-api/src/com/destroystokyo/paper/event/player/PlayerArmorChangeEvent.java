package com.destroystokyo.paper.event.player;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
public class PlayerArmorChangeEvent extends Event {
    public Player getPlayer() { return null; }
    public ItemStack getNewItem() { return null; }
    public ItemStack getPreviousItem() { return null; }
}
