package org.bukkit.event.player;

import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;

public class PlayerInteractEvent extends PlayerEvent implements Cancellable {
    public Block getClickedBlock() { return null; }
    public Action getAction() { return null; }
    public EquipmentSlot getHand() { return null; }
    public org.bukkit.inventory.ItemStack getItem() { return null; }
    public Result useItemInHand() { return Result.DEFAULT; }
    public Result useInteractedBlock() { return getClickedBlock() == null ? Result.DENY : Result.ALLOW; }
    @Override public void setCancelled(boolean cancelled) {}
    @Override public boolean isCancelled() { return useInteractedBlock() == Result.DENY; }
}
