package org.bukkit.event.inventory;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.Inventory;
public class InventoryClickEvent extends InventoryEvent implements Cancellable {
    private boolean cancelled;
    private HumanEntity who;
    private Inventory clicked;
    private int rawSlot;
    private ClickType click = ClickType.LEFT;
    public InventoryClickEvent(Inventory inventory, Inventory clicked, HumanEntity who, int rawSlot, ClickType click) {
        this.inventory = inventory; this.clicked = clicked; this.who = who; this.rawSlot = rawSlot; this.click = click;
    }
    public HumanEntity getWhoClicked() { return who; }
    public Inventory getClickedInventory() { return clicked; }
    public int getRawSlot() { return rawSlot; }
    public ClickType getClick() { return click; }
    public boolean isShiftClick() { return click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
