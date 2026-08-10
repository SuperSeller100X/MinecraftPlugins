package com.coderewards;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;

public class CodeGuiManager {
    
    public static void openCodesGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, ChatColor.GOLD + "Code Rewards");
        
        // Fill background with purple glass
        ItemStack glass = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(" ");
        glass.setItemMeta(meta);
        
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                gui.setItem(i, glass);
            }
        }
        
        // Info item
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.YELLOW + "How to Redeem Codes");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.WHITE + "1. Get a code from an event");
        lore.add(ChatColor.WHITE + "2. Type /redeem <code>");
        lore.add(ChatColor.WHITE + "3. Receive your reward!");
        lore.add("");
        lore.add(ChatColor.GREEN + "Active Codes: " + ChatColor.WHITE + CodeRewardsPlugin.getInstance().getActiveCodes().size());
        infoMeta.setLore(lore);
        info.setItemMeta(infoMeta);
        gui.setItem(22, info);
        
        // Redeem prompt
        ItemStack redeemPrompt = new ItemStack(Material.NAME_TAG);
        ItemMeta redeemMeta = redeemPrompt.getItemMeta();
        redeemMeta.setDisplayName(ChatColor.GREEN + "Redeem a Code");
        List<String> redeemLore = new ArrayList<>();
        redeemLore.add(ChatColor.WHITE + "Click to open chat");
        redeemLore.add(ChatColor.WHITE + "and type your code!");
        redeemMeta.setLore(redeemLore);
        redeemPrompt.setItemMeta(redeemMeta);
        gui.setItem(49, redeemPrompt);
        
        // Show active codes (up to 6)
        int slot = 10;
        int count = 0;
        for (String codeStr : CodeRewardsPlugin.getInstance().getActiveCodes().keySet()) {
            if (count >= 6) break;
            
            Code code = CodeRewardsPlugin.getInstance().getActiveCodes().get(codeStr);
            if (!code.isActive()) continue;
            
            ItemStack codeItem = new ItemStack(Material.PAPER);
            ItemMeta codeMeta = codeItem.getItemMeta();
            codeMeta.setDisplayName(ChatColor.AQUA + "Code: " + code.getCode());
            List<String> codeLore = new ArrayList<>();
            codeLore.add(ChatColor.WHITE + "Reward: " + code.getRewardType());
            codeLore.add(ChatColor.WHITE + "Amount: " + (int)code.getAmount());
            codeLore.add("");
            codeLore.add(ChatColor.GREEN + "Status: Active");
            codeMeta.setLore(codeLore);
            codeItem.setItemMeta(codeMeta);
            gui.setItem(slot, codeItem);
            
            slot++;
            if (slot % 9 == 0) slot += 2; // Skip border
            count++;
        }
        
        player.openInventory(gui);
    }
}
