package dev.superseller.playervault.gui;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Identifies a vault inventory so click handling can tell a PlayerVault GUI apart
 * from any other chest on the server.
 *
 * <p>Also carries the page index and the layout used to build the view, which keeps
 * every slot calculation in one place instead of being recomputed per event.
 */
public final class VaultHolder implements InventoryHolder {

    private final UUID owner;
    private final int page;
    private final GuiLayout layout;
    private final boolean adminView;
    private Inventory inventory;

    public VaultHolder(UUID owner, int page, GuiLayout layout, boolean adminView) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.page = Math.max(0, page);
        this.layout = Objects.requireNonNull(layout, "layout");
        this.adminView = adminView;
    }

    /** Whose vault this is. */
    public UUID owner() {
        return owner;
    }

    /** Zero-based page index currently displayed. */
    public int page() {
        return page;
    }

    public GuiLayout layout() {
        return layout;
    }

    /**
     * {@code true} when somebody other than the owner is looking at this vault.
     * Money and inventory buttons are hidden in that case, because they would
     * operate on the viewer instead of the owner.
     */
    public boolean adminView() {
        return adminView;
    }

    /** Set once, right after the inventory is created. */
    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
