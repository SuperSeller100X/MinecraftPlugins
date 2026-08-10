package com.plugins.coderewards.listeners;

import com.plugins.coderewards.CodeRewards;
import com.plugins.coderewards.gui.CodeRewardsGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerListener implements Listener {
    private final CodeRewards plugin;

    public PlayerListener(CodeRewards plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        
        if (!(event.getInventory().getHolder() instanceof CodeRewardsGUI)) return;
        
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        
        // Only allow clicks in the top inventory
        if (slot < 0 || slot >= event.getInventory().getSize()) return;
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;
        
        // Handle item clicks - could add copy to clipboard functionality here
        // For now, just refresh the GUI to show updated info
        if (slot >= 9 && slot <= 44) {
            // Code item clicked - in a real implementation with ProtocolLib, 
            // we could copy the code name to clipboard
            // For vanilla, we'll just send a message
            if (clickedItem.getItemMeta().hasDisplayName()) {
                String displayName = clickedItem.getItemMeta().getDisplayName();
                if (displayName.contains("Code:")) {
                    String codeName = displayName.replace("&e&lCode: ", "").replaceAll("[^A-Z0-9]", "");
                    player.sendMessage("§8[§6CodeRewards§8] §7Code copied: §e" + codeName);
                    player.sendMessage("§8[§6CodeRewards§8] §7Type §e/coderedeem " + codeName + " §7to redeem!");
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Player player = (Player) event.getPlayer();
        
        if (!(event.getInventory().getHolder() instanceof CodeRewardsGUI)) return;
        
        // GUI closed - could save state or perform cleanup if needed
    }
}
