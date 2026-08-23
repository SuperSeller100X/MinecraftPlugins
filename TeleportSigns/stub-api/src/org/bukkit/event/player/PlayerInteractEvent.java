package org.bukkit.event.player;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
public class PlayerInteractEvent extends PlayerEvent implements Cancellable {
    public Action getAction() { return Action.RIGHT_CLICK_BLOCK; }
    public EquipmentSlot getHand() { return EquipmentSlot.HAND; }
    public Block getClickedBlock() { return null; }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) {}
}
