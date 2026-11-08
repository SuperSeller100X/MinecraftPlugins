package org.bukkit.event.inventory;
import java.util.Set;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Event;
import org.bukkit.inventory.InventoryView;
public class InventoryDragEvent extends Event {
    public HumanEntity getWhoClicked() { return null; }
    public InventoryView getView() { return null; }
    public Set<Integer> getRawSlots() { return Set.of(); }
    public void setCancelled(boolean cancelled) {}
}
