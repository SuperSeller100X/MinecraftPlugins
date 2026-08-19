package org.bukkit.event.inventory;

import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

public abstract class InventoryEvent extends Event {
    public enum Result {
        DENY,
        DEFAULT,
        ALLOW
    }

    public InventoryView getView() {
        return null;
    }

    public Inventory getInventory() {
        return null;
    }

    public Result getResult() {
        return null;
    }

    public void setResult(Result result) {
    }
}
