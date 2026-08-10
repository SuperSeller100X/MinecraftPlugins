package com.giftsystem;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class GiftGuiManager {
    
    public static void openGiftGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, ChatColor.GOLD + "Create a Gift");
        
        // Fill background with gray glass
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(" ");
        glass.setItemMeta(meta);
        
        for (int i = 0; i < 54; i++) {
            if (i != 22 && i != 49) {
                gui.setItem(i, glass);
            }
        }
        
        // Center item - Info
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.YELLOW + "How to Create a Gift");
        infoMeta.setLore(java.util.Arrays.asList(
            ChatColor.WHITE + "1. Place items in the",
            ChatColor.WHITE + "   middle slots",
            ChatColor.WHITE + "2. Click the green glass",
            ChatColor.WHITE + "   to confirm",
            ChatColor.WHITE + "3. Use /gift <player>",
            ChatColor.WHITE + "   to send"
        ));
        info.setItemMeta(infoMeta);
        gui.setItem(22, info);
        
        // Confirm button
        ItemStack confirm = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.GREEN + "Confirm Gift");
        confirm.setItemMeta(confirmMeta);
        gui.setItem(49, confirm);
        
        player.openInventory(gui);
    }
}
