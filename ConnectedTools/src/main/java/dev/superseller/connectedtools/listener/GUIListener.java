package dev.superseller.connectedtools.listener;

import dev.superseller.connectedtools.gui.MenuHolder;
import dev.superseller.connectedtools.model.ConnectionStore;
import dev.superseller.connectedtools.util.Colors;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class GUIListener implements Listener {

    private final ConnectionStore store;

    public GUIListener(ConnectionStore store) {
        this.store = store;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().contains("Connected Tools") || event.getInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) {
                return;
            }
            if (event.getWhoClicked() instanceof Player player) {
                if (event.getInventory().getHolder() instanceof MenuHolder holder) {
                    if (!holder.viewer().equals(player.getUniqueId())) {
                        return;
                    }
                    int slot = event.getRawSlot();
                    if (slot >= 0 && slot < event.getInventory().getSize()) {
                        handleClick(player, slot, holder);
                    }
                }
            }
        }
    }

    private void handleClick(Player player, int slot, MenuHolder holder) {
        // Disconnect on click for any slot that has an item
        java.util.List<dev.superseller.connectedtools.model.Connection> connections = store.getConnections(player.getUniqueId());
        if (slot < connections.size()) {
            dev.superseller.connectedtools.model.Connection conn = connections.get(slot);
            // Find and remove by serialized location match
            java.util.Map<java.util.UUID, java.util.Map<String, dev.superseller.connectedtools.model.Connection>> internalData = store.getData();
            java.util.Map<String, dev.superseller.connectedtools.model.Connection> playerData = internalData.getOrDefault(player.getUniqueId(), new java.util.HashMap<>());
            String targetLocation = conn.getSerializedLocation();
            String keyToRemove = null;
            for (java.util.Map.Entry<String, dev.superseller.connectedtools.model.Connection> entry : new java.util.HashMap<>(playerData).entrySet()) {
                if (entry.getValue().getSerializedLocation().equals(targetLocation)) {
                    keyToRemove = entry.getKey();
                    break;
                }
            }
            if (keyToRemove != null) {
                playerData.remove(keyToRemove);
            }
            player.sendMessage(dev.superseller.connectedtools.util.Colors.parse("&aDisconnected: " + conn.getItemName()));
            player.closeInventory();
            // Reopen to refresh
            new dev.superseller.connectedtools.gui.GuiManager(
                    dev.superseller.connectedtools.ConnectedToolsPlugin.getInstance().settings(), store).openMain(player);
        }
    }
}
