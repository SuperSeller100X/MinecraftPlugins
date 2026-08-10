package com.plugins.coderewards.gui;

import com.plugins.coderewards.CodeRewards;
import com.plugins.coderewards.models.Code;
import com.plugins.coderewards.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class CodeRewardsGUI implements InventoryHolder {
    private final CodeRewards plugin;
    private Inventory inventory;
    private final Player viewer;

    public CodeRewardsGUI(CodeRewards plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        createInventory();
    }

    private void createInventory() {
        String title = MessageUtils.colorize(plugin.getConfig().getString("messages.gui-title", "&6Code Rewards"));
        inventory = Bukkit.createInventory(this, 54, title);
        
        // Fill background
        fillBackground();
        
        // Add header items
        addHeaderItems();
        
        // Add active codes
        addCodeItems();
        
        // Add info item
        addInfoItem();
    }

    private void fillBackground() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
        }
        
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, glass);
            }
        }
    }

    private void addHeaderItems() {
        // Title item
        ItemStack titleItem = new ItemStack(Material.BOOK);
        ItemMeta meta = titleItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtils.colorize("&6&lAvailable Codes"));
            List<String> lore = new ArrayList<>();
            lore.add(MessageUtils.colorize(""));
            lore.add(MessageUtils.colorize("&7Click on a code to see details!"));
            lore.add(MessageUtils.colorize("&7Use &e/coderedeem <code>&7 to redeem."));
            lore.add(MessageUtils.colorize(""));
            meta.setLore(lore);
            titleItem.setItemMeta(meta);
        }
        inventory.setItem(13, titleItem);
        
        // Stats item
        int activeCodes = plugin.getCodeManager().getActiveCodeCount();
        int totalCodes = plugin.getCodeManager().getTotalCodes();
        
        ItemStack statsItem = new ItemStack(Material.PAPER);
        ItemMeta statsMeta = statsItem.getItemMeta();
        if (statsMeta != null) {
            statsMeta.setDisplayName(MessageUtils.colorize("&a&lStatistics"));
            List<String> lore = new ArrayList<>();
            lore.add(MessageUtils.colorize(""));
            lore.add(MessageUtils.colorize("&7Active Codes: &e" + activeCodes));
            lore.add(MessageUtils.colorize("&7Total Codes: &e" + totalCodes));
            lore.add(MessageUtils.colorize(""));
            statsMeta.setLore(lore);
            statsItem.setItemMeta(statsMeta);
        }
        inventory.setItem(49, statsItem);
    }

    private void addCodeItems() {
        int slot = 9;
        List<Code> activeCodes = plugin.getCodeManager().getActiveCodes();
        
        if (activeCodes.isEmpty()) {
            ItemStack noCodes = new ItemStack(Material.BARRIER);
            ItemMeta meta = noCodes.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(MessageUtils.colorize("&c&lNo Active Codes"));
                List<String> lore = new ArrayList<>();
                lore.add(MessageUtils.colorize(""));
                lore.add(MessageUtils.colorize("&7There are currently no active codes."));
                lore.add(MessageUtils.colorize("&7Check back later!"));
                lore.add(MessageUtils.colorize(""));
                meta.setLore(lore);
                noCodes.setItemMeta(meta);
            }
            inventory.setItem(22, noCodes);
            return;
        }
        
        for (Code code : activeCodes) {
            if (slot >= 45) break; // Limit to available slots
            
            ItemStack codeItem = new ItemStack(Material.EXPERIENCE_BOTTLE);
            ItemMeta meta = codeItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(MessageUtils.colorize("&e&lCode: " + code.getCode()));
                List<String> lore = new ArrayList<>();
                lore.add(MessageUtils.colorize(""));
                lore.add(MessageUtils.colorize("&7Type: &f" + capitalize(code.getRewardType())));
                
                if (code.getMaxUses() > 0) {
                    int remaining = code.getMaxUses() - code.getUses();
                    lore.add(MessageUtils.colorize("&7Uses: &f" + code.getUses() + "/" + code.getMaxUses() + " (&e" + remaining + " remaining&f)"));
                } else {
                    lore.add(MessageUtils.colorize("&7Uses: &fUnlimited"));
                }
                
                if (code.getExpiresAt() > 0) {
                    long timeLeft = (code.getExpiresAt() - System.currentTimeMillis()) / 1000;
                    if (timeLeft > 0) {
                        lore.add(MessageUtils.colorize("&7Expires in: &e" + MessageUtils.formatTime(timeLeft)));
                    } else {
                        lore.add(MessageUtils.colorize("&7Status: &cExpired"));
                    }
                }
                
                lore.add(MessageUtils.colorize(""));
                lore.add(MessageUtils.colorize("&7Rewards:"));
                for (String reward : code.getRewards()) {
                    lore.add(MessageUtils.colorize("  &8- &f" + reward));
                }
                lore.add(MessageUtils.colorize(""));
                lore.add(MessageUtils.colorize("&e&lClick to copy code!"));
                
                meta.setLore(lore);
                codeItem.setItemMeta(meta);
            }
            
            inventory.setItem(slot, codeItem);
            slot++;
        }
    }

    private void addInfoItem() {
        ItemStack infoItem = new ItemStack(Material.EMERALD);
        ItemMeta meta = infoItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtils.colorize("&a&lHow to Redeem"));
            List<String> lore = new ArrayList<>();
            lore.add(MessageUtils.colorize(""));
            lore.add(MessageUtils.colorize("&71. Click on a code above"));
            lore.add(MessageUtils.colorize("&72. Copy the code name"));
            lore.add(MessageUtils.colorize("&73. Type &e/coderedeem <code>"));
            lore.add(MessageUtils.colorize("&74. Receive your rewards!"));
            lore.add(MessageUtils.colorize(""));
            lore.add(MessageUtils.colorize("&eTip: Codes are case-insensitive!"));
            meta.setLore(lore);
            infoItem.setItemMeta(meta);
        }
        inventory.setItem(53, infoItem);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    public void refresh() {
        createInventory();
        viewer.openInventory(inventory);
    }
}
