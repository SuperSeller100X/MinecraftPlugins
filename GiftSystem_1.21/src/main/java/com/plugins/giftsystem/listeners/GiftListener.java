package com.plugins.giftsystem.listeners;

import com.plugins.giftsystem.GiftSystem;
import com.plugins.giftsystem.gui.GiftInboxGUI;
import com.plugins.giftsystem.models.Gift;
import com.plugins.giftsystem.utils.MessageUtils;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class GiftListener implements Listener {
    private final GiftSystem plugin;

    public GiftListener(GiftSystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        
        if (!(event.getInventory().getHolder() instanceof GiftInboxGUI)) return;
        
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;
        
        GiftInboxGUI gui = (GiftInboxGUI) event.getInventory().getHolder();
        List<Gift> gifts = gui.getGifts();
        int page = gui.getPage();
        int itemsPerPage = 45;
        int startIndex = page * itemsPerPage;
        
        // Handle gift opening (slots 0-44)
        if (slot >= 0 && slot < 45 && slot < gifts.size()) {
            if (startIndex + slot < gifts.size()) {
                Gift gift = gifts.get(startIndex + slot);
                
                if (gift.isAvailable()) {
                    // Open the gift
                    openGift(player, gift);
                    
                    // Refresh GUI
                    new GiftInboxGUI(plugin, player, page).open();
                }
            }
        }
        
        // Handle navigation
        if (slot == 48 && page > 0) {
            // Previous page
            new GiftInboxGUI(plugin, player, page - 1).open();
        } else if (slot == 50) {
            // Next page
            int totalPages = (int) Math.ceil((double) gifts.size() / itemsPerPage);
            if (page < totalPages - 1) {
                new GiftInboxGUI(plugin, player, page + 1).open();
            }
        }
    }

    private void openGift(Player player, Gift gift) {
        // Give items to player
        StringBuilder itemSummary = new StringBuilder();
        for (ItemStack item : gift.getItems()) {
            player.getInventory().addItem(item.clone());
            itemSummary.append(item.getAmount()).append("x ").append(item.getType().name()).append(", ");
        }
        
        // Remove trailing comma
        if (itemSummary.length() > 2) {
            itemSummary.setLength(itemSummary.length() - 2);
        }
        
        // Mark as opened
        gift.setOpened(true);
        plugin.getGiftManager().saveGifts();
        
        // Send message
        String msg = plugin.getConfig().getString("messages.gift-opened", "&aYou opened a gift from &e%player%&a! Received: %items%");
        msg = msg.replace("%player%", gift.getSenderName());
        msg = msg.replace("%items%", itemSummary.toString());
        player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.prefix", "&8[&aGiftSystem&8] ") + msg));
        
        // Play sound
        playSound(player, "gift-opened");
    }

    private void playSound(Player player, String type) {
        String soundName = plugin.getConfig().getString("sounds." + type, "");
        if (!soundName.isEmpty()) {
            try {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                // Sound not found, skip
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        // Could save state here if needed
    }
}
