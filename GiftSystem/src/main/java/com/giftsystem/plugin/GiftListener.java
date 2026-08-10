package com.giftsystem.plugin;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class GiftListener implements Listener {

    private final GiftSystemPlugin plugin;

    public GiftListener(GiftSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        
        // Check if clicking in gift GUI
        if (event.getView().getTitle().equals("§6Your Gifts")) {
            event.setCancelled(true);

            int slot = event.getSlot();
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }

            // Check if it's a filler item
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta != null && meta.hasDisplayName() && meta.getDisplayName().equals(" ")) {
                return;
            }

            // This is a gift item - claim it
            List<Gift> gifts = plugin.getPlayerGifts(player.getUniqueId());
            
            if (slot >= 0 && slot < gifts.size()) {
                Gift gift = gifts.get(slot);
                ItemStack giftItem = gift.getItem().clone();

                // Try to give item to player
                if (player.getInventory().firstEmpty() == -1) {
                    player.sendMessage("§cYour inventory is full! Make room to claim gifts.");
                    return;
                }

                player.getInventory().addItem(giftItem);
                plugin.removeGift(player.getUniqueId(), slot);
                
                player.sendMessage("§aYou claimed a gift from §e" + gift.getFromPlayer() + "!");
                
                // Update GUI
                player.openInventory(plugin.createGiftGUI(player));
            }
        }
    }
}
