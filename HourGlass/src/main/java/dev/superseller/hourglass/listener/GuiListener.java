package dev.superseller.hourglass.listener;

import dev.superseller.hourglass.HourGlassPlugin;
import dev.superseller.hourglass.gui.GuiHolder;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;

/**
 * Handles clicks, drags and closes for HourGlass windows.
 *
 * <p>A window is recognised by its {@link GuiHolder}, never by its title, so
 * renaming another plugin's chest can neither trigger our actions nor leak into
 * them. Every interaction is cancelled outright — including shift-clicks,
 * number-key swaps, off-hand swaps and drags across the top inventory — because
 * a playtime GUI must not become a way to duplicate or steal items.
 */
public final class GuiListener implements Listener {

    private final HourGlassPlugin plugin;

    public GuiListener(HourGlassPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        GuiHolder holder = holderOf(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer) || !viewer.isOnline()) {
            return;
        }
        if (event.getClickedInventory() != null && event.getClickedInventory() != holder.getInventory()) {
            return; // their own inventory — already cancelled, nothing to run
        }
        plugin.gui().handleClick(viewer, holder, event.getSlot(), event.getClick());
    }

    /**
     * Drag events (the "split stack over many slots" gesture) must be blocked
     * too, otherwise a drag started inside the GUI can push items into it.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (holderOf(event.getView().getTopInventory()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        GuiHolder holder = holderOf(event.getInventory());
        if (holder == null || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        plugin.sounds().play(player, "gui-close");
    }

    /** Optionally closes an open GUI when the player walks away. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.config().guiCloseOnMove() || !event.hasChangedBlock()) {
            return;
        }
        Player player = event.getPlayer();
        Inventory top = player.getOpenInventory() == null ? null : player.getOpenInventory().getTopInventory();
        if (holderOf(top) != null) {
            player.closeInventory();
        }
    }

    private GuiHolder holderOf(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        return inventory.getHolder() instanceof GuiHolder holder ? holder : null;
    }
}
