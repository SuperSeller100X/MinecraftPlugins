package org.bukkit.event.inventory;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class InventoryClickEvent extends InventoryInteractEvent implements Cancellable {
    private boolean cancelled;

    public Inventory getClickedInventory() {
        return null;
    }

    public int getSlot() {
        return 0;
    }

    public int getRawSlot() {
        return 0;
    }

    public ItemStack getCurrentItem() {
        return null;
    }

    public ItemStack getCursor() {
        return null;
    }

    public ClickType getClick() {
        return null;
    }

    public boolean isLeftClick() {
        return false;
    }

    public boolean isRightClick() {
        return false;
    }

    public boolean isShiftClick() {
        return false;
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
