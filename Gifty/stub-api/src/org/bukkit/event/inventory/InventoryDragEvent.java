package org.bukkit.event.inventory;

import java.util.Map;
import java.util.Set;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.ItemStack;

public class InventoryDragEvent extends InventoryInteractEvent implements Cancellable {
    private boolean cancelled;

    public Map<Integer, ItemStack> getNewItems() {
        return null;
    }

    public Set<Integer> getRawSlots() {
        return null;
    }

    public ItemStack getCursor() {
        return null;
    }

    public HumanEntity getWhoClicked() {
        return null;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
