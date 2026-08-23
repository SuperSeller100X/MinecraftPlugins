package dev.superseller.connectedtools.listener;

import dev.superseller.connectedtools.gui.ConnectionGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class GUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().contains("Connected Tools")) {
            event.setCancelled(true);
            if (event.getRawSlot() < event.getInventory().getSize() && event.getCurrentItem() != null) {
                Player player = (Player) event.getWhoClicked();
                ConnectionGUI.handleClick(player, event.getRawSlot());
            }
        }
    }
}
