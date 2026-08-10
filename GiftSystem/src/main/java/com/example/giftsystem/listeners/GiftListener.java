package com.example.giftsystem.listeners;

import com.example.giftsystem.GiftSystemPlugin;
import com.example.giftsystem.data.Gift;
import com.example.giftsystem.gui.GiftGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class GiftListener implements Listener {
    private final GiftSystemPlugin plugin;

    public GiftListener(GiftSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        
        if (event.getInventory().getHolder() instanceof GiftGUI) {
            event.setCancelled(true);
            
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || !clickedItem.hasItemMeta()) return;
            
            String displayName = clickedItem.getItemMeta().getDisplayName();
            if (displayName == null) return;
            
            // Check if clicking a gift
            if (displayName.contains("Gift from")) {
                String senderName = displayName.replace("§aGift from ", "");
                claimGift(player, senderName);
            }
        }
    }

    private void claimGift(Player player, String senderName) {
        Gift giftToClaim = null;
        
        for (Gift gift : plugin.getGiftManager().getUnclaimedGifts(player.getUniqueId().toString())) {
            if (gift.getSenderName().equals(senderName)) {
                giftToClaim = gift;
                break;
            }
        }
        
        if (giftToClaim == null) {
            player.sendMessage("§cGift not found!");
            return;
        }

        // Check if inventory has space
        boolean hasSpace = false;
        for (ItemStack item : giftToClaim.getItems()) {
            if (player.getInventory().firstEmpty() != -1 || 
                player.getInventory().containsAtLeast(item, item.getAmount())) {
                hasSpace = true;
            }
        }
        
        if (!hasSpace) {
            player.sendMessage("§cYour inventory is full! Clear some space and try again.");
            return;
        }

        // Give items
        for (ItemStack item : giftToClaim.getItems()) {
            if (item != null) {
                player.getInventory().addItem(item.clone());
            }
        }

        // Mark as claimed
        giftToClaim.claim();
        plugin.getGiftManager().saveGifts();

        player.sendMessage("§aYou claimed a gift from " + giftToClaim.getSenderName() + "!");
        if (!giftToClaim.getMessage().isEmpty()) {
            player.sendMessage("§7Message: " + giftToClaim.getMessage());
        }

        // Refresh GUI
        GiftGUI.openGUI(player);
    }
}
