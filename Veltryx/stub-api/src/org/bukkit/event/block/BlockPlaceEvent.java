package org.bukkit.event.block;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

public class BlockPlaceEvent extends Event implements Cancellable {
    public Player getPlayer() { return null; }
    public org.bukkit.inventory.ItemStack getItemInHand() { return null; }
    public org.bukkit.block.Block getBlockPlaced() { return null; }
    public boolean canBuild() { return true; }
    @Override public void setCancelled(boolean cancelled) {}
    @Override public boolean isCancelled() { return false; }
}
