package dev.superseller.subscriptions.gui;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MenuHolder implements InventoryHolder {

    public enum Menu {
        MAIN, BROWSE, PLAN, CREATE, MY_SUBS, MY_PLANS, INBOX, HISTORY, STOCK, POLICY, CONFIRM
    }

    public final Menu menu;
    public final UUID viewer;
    public int page;
    public String planId;
    public String subscriptionId;
    public String query = "";
    public String category = "ALL";
    public String confirmAction;
    public long[] inboxIds = new long[0];
    public String[] slotIds = new String[54];
    private Inventory inventory;

    public MenuHolder(Menu menu, UUID viewer) {
        this.menu = menu;
        this.viewer = viewer;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
