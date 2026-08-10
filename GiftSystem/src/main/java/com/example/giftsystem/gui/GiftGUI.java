package com.example.giftsystem.gui;

import com.example.giftsystem.GiftSystemPlugin;
import com.example.giftsystem.data.Gift;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class GiftGUI implements InventoryHolder {
    private final Player player;
    private Inventory inventory;

    public GiftGUI(Player player) {
        this.player = player;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, 54, "§6Your Gifts");
        initializeItems();
        player.openInventory(inventory);
    }

    private void initializeItems() {
        // Fill background
        ItemStack glass = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, glass);
        }

        // Get unclaimed gifts
        GiftSystemPlugin plugin = GiftSystemPlugin.getInstance();
        List<Gift> gifts = plugin.getGiftManager().getUnclaimedGifts(player.getUniqueId().toString());
        
        int slot = 10;
        for (Gift gift : gifts) {
            if (slot >= inventory.getSize() - 9) break;
            
            Material material = Material.CHEST;
            String name = "§aGift from " + gift.getSenderName();
            
            List<String> lore = new ArrayList<>();
            lore.add("§7Sender: " + gift.getSenderName());
            lore.add("§7Items: " + gift.getItems().size());
            if (!gift.getMessage().isEmpty()) {
                lore.add("");
                lore.add("§eMessage: " + gift.getMessage());
            }
            lore.add("");
            lore.add("§eClick to claim!");
            
            ItemStack item = createGuiItem(material, name, lore);
            inventory.setItem(slot, item);
            
            slot++;
            if ((slot + 1) % 9 == 0) slot += 2;
        }

        // Info item
        ItemStack info = createGuiItem(Material.BOOK, "§bHow to Use", 
                "§7Click on a gift to claim it!",
                "§7Items will be added to your inventory.");
        inventory.setItem(49, info);
        
        if (gifts.isEmpty()) {
            ItemStack noGifts = createGuiItem(Material.BARRIER, "§cNo Pending Gifts",
                    "§7You have no unclaimed gifts.");
            inventory.setItem(22, noGifts);
        }
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
        GiftGUI gui = new GiftGUI(player);
        gui.open();
    }
}
