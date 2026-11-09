package org.bukkit.event.player;

import org.bukkit.event.Cancellable;

public class PlayerItemConsumeEvent extends PlayerEvent implements Cancellable {
    public org.bukkit.inventory.ItemStack getItem() { return null; }
    public void setItem(org.bukkit.inventory.ItemStack item) {}
    @Override public void setCancelled(boolean cancelled) {}
    @Override public boolean isCancelled() { return false; }
}
