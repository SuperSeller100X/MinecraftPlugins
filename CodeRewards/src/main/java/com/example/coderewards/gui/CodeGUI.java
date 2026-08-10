package com.example.coderewards.gui;

import com.example.coderewards.CodeRewardsPlugin;
import com.example.coderewards.data.RewardCode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class CodeGUI implements InventoryHolder {
    private final Player player;
    private Inventory inventory;

    public CodeGUI(Player player) {
        this.player = player;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, 54, "§6Reward Codes");
        initializeItems();
        player.openInventory(inventory);
    }

    private void initializeItems() {
        // Fill background
        ItemStack glass = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, glass);
        }

        // Add codes
        CodeRewardsPlugin plugin = CodeRewardsPlugin.getInstance();
        int slot = 10;
        
        for (RewardCode code : plugin.getCodeManager().getAllCodes()) {
            if (slot >= inventory.getSize() - 9) break;
            
            Material material = code.canRedeem() ? Material.EMERALD : Material.BARRIER;
            String name = code.canRedeem() ? "§a" + code.getCode() : "§c" + code.getCode() + " §7(Expired)";
            
            List<String> lore = new ArrayList<>();
            lore.add("§7Uses: " + code.getUsesLeft() + "/" + code.getMaxUses());
            lore.add("§7One Time Per Player: " + (code.isOneTimePerPlayer() ? "Yes" : "No"));
            
            if (code.hasRedeemed(player.getUniqueId().toString())) {
                lore.add("");
                lore.add("§cAlready redeemed!");
            } else {
                lore.add("");
                lore.add("§eClick to redeem!");
            }
            
            ItemStack item = createGuiItem(material, name, lore);
            inventory.setItem(slot, item);
            
            slot++;
            if ((slot + 1) % 9 == 0) slot += 2; // Skip row separators
        }

        // Info item
        ItemStack info = createGuiItem(Material.BOOK, "§bHow to Redeem", 
                "§7Use /code <code>",
                "§7Or click a code above!");
        inventory.setItem(49, info);
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> loreList = new ArrayList<>();
        for (String line : lore) {
            loreList.add(line);
        }
        meta.setLore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGuiItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static void openGUI(Player player) {
        CodeGUI gui = new CodeGUI(player);
        gui.open();
    }
}
