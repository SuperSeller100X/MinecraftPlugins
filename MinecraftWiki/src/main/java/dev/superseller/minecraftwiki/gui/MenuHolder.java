package dev.superseller.minecraftwiki.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * The inventory holder that ties an open inventory back to the menu that built it.
 *
 * <p>Routing clicks through the holder rather than through the inventory title means a
 * renamed title, a translated title or two menus with the same title can never confuse the
 * click handler, and no inventory outside the wiki is ever affected.</p>
 */
public final class MenuHolder implements InventoryHolder {

    private WikiMenu menu;
    private Inventory inventory;

    public WikiMenu menu() {
        return menu;
    }

    public void menu(WikiMenu menu) {
        this.menu = menu;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    /** True when this holder still owns a live wiki menu. */
    public boolean active() {
        return menu != null;
    }
}
