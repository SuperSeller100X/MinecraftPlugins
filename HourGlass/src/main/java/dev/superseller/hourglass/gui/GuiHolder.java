package dev.superseller.hourglass.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marks an inventory as belonging to HourGlass and remembers which slot does
 * what, so the click listener never has to inspect item metadata or guess from
 * slot numbers. Because the mapping lives on the holder, a player cannot trick
 * the plugin into running an action by renaming an item and putting it in a
 * chest: only inventories we created have a holder of this type.
 */
public final class GuiHolder implements InventoryHolder {

    private final String screenId;
    private final UUID viewer;
    /** What a slot does when clicked; receives the clicker and the click type. */
    @FunctionalInterface
    public interface ClickAction {
        void run(org.bukkit.entity.Player viewer, ClickType click);
    }

    private final Map<Integer, ClickAction> actions = new HashMap<>();
    private Inventory inventory;
    private int page;
    private UUID target;

    public GuiHolder(String screenId, UUID viewer, int page) {
        this.screenId = screenId;
        this.viewer = viewer;
        this.page = page;
    }

    /** Set right after the inventory is created (the holder must exist first). */
    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public String screenId() {
        return screenId;
    }

    public UUID viewer() {
        return viewer;
    }

    public int page() {
        return page;
    }

    public void page(int page) {
        this.page = Math.max(0, page);
    }

    /** The player this screen is about (admin detail view), may be {@code null}. */
    public UUID target() {
        return target;
    }

    public void target(UUID target) {
        this.target = target;
    }

    public void bind(int slot, ClickAction action) {
        if (action != null) {
            actions.put(slot, action);
        }
    }

    public ClickAction action(int slot) {
        return actions.get(slot);
    }

    public boolean hasActions() {
        return !actions.isEmpty();
    }
}
