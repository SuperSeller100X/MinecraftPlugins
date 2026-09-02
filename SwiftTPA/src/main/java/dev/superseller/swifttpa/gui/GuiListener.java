package dev.superseller.swifttpa.gui;

import dev.superseller.swifttpa.SwiftTPAPlugin;
import dev.superseller.swifttpa.util.Sounds;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles clicks inside the requests GUI. All item movement is cancelled (the
 * menu is display-only); head clicks accept (left) or deny (right) the tagged
 * request, and the bottom bar hosts the request toggle and the
 * cancel-my-request button. Actions run on the clicker's own thread, then the
 * service repaints every affected viewer.
 */
public final class GuiListener implements Listener {

    private final SwiftTPAPlugin plugin;
    private final RequestsGui gui;

    public GuiListener(SwiftTPAPlugin plugin, RequestsGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof GuiHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !(clicked.getHolder() instanceof GuiHolder)) {
            return; // Bottom (player) inventory: ignore but stay cancelled.
        }

        int slot = event.getSlot();
        if (slot == RequestsGui.TOGGLE_SLOT) {
            Sounds.play(plugin.tpaConfig(), player, "gui-click");
            plugin.service().toggle(player);
            return;
        }
        if (slot == RequestsGui.CANCEL_SLOT) {
            Sounds.play(plugin.tpaConfig(), player, "gui-click");
            plugin.service().cancel(player);
            return;
        }
        if (slot < 0 || slot >= 45) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        String tag = meta.getPersistentDataContainer().get(gui.requestKey(), PersistentDataType.STRING);
        if (tag == null || !tag.contains("|")) {
            return;
        }
        String senderName = tag.substring(tag.indexOf('|') + 1);
        Sounds.play(plugin.tpaConfig(), player, "gui-click");
        if (event.getClick().isRightClick()) {
            plugin.service().deny(player, senderName);
        } else {
            plugin.service().accept(player, senderName);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof GuiHolder && event.getPlayer() instanceof Player player) {
            gui.closed(player);
        }
    }
}
