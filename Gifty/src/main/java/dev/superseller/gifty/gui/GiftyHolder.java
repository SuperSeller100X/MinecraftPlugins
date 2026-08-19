package dev.superseller.gifty.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Holder marker + session state for every Gifty GUI.
 */
public final class GiftyHolder implements InventoryHolder {

    public enum Kind {
        COMPOSE,   // free item grid
        CONFIRM,   // summary + actions
        INBOX      // deliveries, paged
    }

    public final Kind kind;
    public final java.util.UUID playerUuid;   // owner of the session (clicker)
    public final java.util.UUID targetId;     // inbox owner / gift recipient
    public final String targetName;
    public int page = 1;
    public boolean readOnly = false;
    public boolean done = false;              // set before programmatic close
    public long[] deliveryIds = new long[0];  // inbox: slot -> delivery id

    private Inventory inventory;

    public GiftyHolder(Kind kind, java.util.UUID playerUuid, java.util.UUID targetId, String targetName) {
        this.kind = kind;
        this.playerUuid = playerUuid;
        this.targetId = targetId;
        this.targetName = targetName;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
