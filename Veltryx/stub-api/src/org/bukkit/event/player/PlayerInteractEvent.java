package org.bukkit.event.player;

import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;

public class PlayerInteractEvent extends PlayerEvent implements Cancellable {
    public Block getClickedBlock() { return null; }
    public Action getAction() { return null; }
    public EquipmentSlot getHand() { return null; }
    @Override public void setCancelled(boolean cancelled) {}
    @Override public boolean isCancelled() { return false; }
}
