package com.giftsystem.plugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;

public class GiftSystemPlugin extends JavaPlugin {

    private static GiftSystemPlugin instance;
    private File giftsFile;
    private FileConfiguration giftsConfig;
    private Map<UUID, List<Gift>> playerGifts;
    private Map<String, Long> giftCooldowns;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        
        // Initialize files
        giftsFile = new File(getDataFolder(), "gifts.yml");
        if (!giftsFile.exists()) {
            try {
                giftsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        giftsConfig = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            giftsConfig.load(giftsFile);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Initialize maps
        playerGifts = new HashMap<>();
        giftCooldowns = new HashMap<>();

        // Load existing gifts
        loadGifts();

        // Register commands
        registerCommands();

        // Register listeners
        getServer().getPluginManager().registerEvents(new GiftListener(this), this);

        getLogger().info("GiftSystem has been enabled!");
    }

    @Override
    public void onDisable() {
        saveGifts();
        getLogger().info("GiftSystem has been disabled!");
    }

    public static GiftSystemPlugin getInstance() {
        return instance;
    }

    private void registerCommands() {
        getCommand("gift").setExecutor(new GiftCommand(this));
        getCommand("receategift").setExecutor(new ReceiveGiftCommand(this));
        getCommand("giftgui").setExecutor(new GiftGUICommand(this));
        getCommand("giftadmin").setExecutor(new GiftAdminCommand(this));
    }

    public void loadGifts() {
        playerGifts.clear();
        for (String uuidStr : giftsConfig.getKeys(false)) {
            UUID uuid = UUID.fromString(uuidStr);
            List<Gift> gifts = new ArrayList<>();
            
            String path = uuidStr + ".";
            int count = giftsConfig.getInt(path + "count", 0);
            
            for (int i = 0; i < count; i++) {
                String giftPath = path + "gift" + i + ".";
                String fromPlayer = giftsConfig.getString(giftPath + "from");
                long timestamp = giftsConfig.getLong(giftPath + "timestamp");
                String message = giftsConfig.getString(giftPath + "message", "");
                
                // Deserialize item
                ItemStack item = null;
                String itemPath = giftPath + "item.";
                if (giftsConfig.contains(itemPath + "material")) {
                    Material material = Material.valueOf(giftsConfig.getString(itemPath + "material"));
                    item = new ItemStack(material);
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        if (giftsConfig.contains(itemPath + "displayname")) {
                            meta.setDisplayName(giftsConfig.getString(itemPath + "displayname"));
                        }
                        if (giftsConfig.contains(itemPath + "lore")) {
                            meta.setLore(giftsConfig.getStringList(itemPath + "lore"));
                        }
                        item.setItemMeta(meta);
                    }
                    int amount = giftsConfig.getInt(itemPath + "amount", 1);
                    item.setAmount(amount);
                }
                
                if (item != null) {
                    gifts.add(new Gift(fromPlayer, item, timestamp, message));
                }
            }
            
            if (!gifts.isEmpty()) {
                playerGifts.put(uuid, gifts);
            }
        }
    }

    public void saveGifts() {
        giftsConfig = new org.bukkit.configuration.file.YamlConfiguration();
        
        for (Map.Entry<UUID, List<Gift>> entry : playerGifts.entrySet()) {
            String uuidStr = entry.getKey().toString();
            List<Gift> gifts = entry.getValue();
            
            giftsConfig.set(uuidStr + ".count", gifts.size());
            
            for (int i = 0; i < gifts.size(); i++) {
                Gift gift = gifts.get(i);
                String path = uuidStr + ".gift" + i + ".";
                
                giftsConfig.set(path + "from", gift.getFromPlayer());
                giftsConfig.set(path + "timestamp", gift.getTimestamp());
                giftsConfig.set(path + "message", gift.getMessage());
                
                // Serialize item
                ItemStack item = gift.getItem();
                if (item != null) {
                    giftsConfig.set(path + "item.material", item.getType().name());
                    giftsConfig.set(path + "item.amount", item.getAmount());
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        if (meta.hasDisplayName()) {
                            giftsConfig.set(path + "item.displayname", meta.getDisplayName());
                        }
                        if (meta.hasLore()) {
                            giftsConfig.set(path + "item.lore", meta.getLore());
                        }
                    }
                }
            }
        }
        
        try {
            giftsConfig.save(giftsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addGift(UUID recipientUuid, Gift gift) {
        playerGifts.computeIfAbsent(recipientUuid, k -> new ArrayList<>()).add(gift);
        saveGifts();
    }

    public List<Gift> getPlayerGifts(UUID playerUuid) {
        return playerGifts.getOrDefault(playerUuid, new ArrayList<>());
    }

    public void removeGift(UUID playerUuid, int index) {
        List<Gift> gifts = playerGifts.get(playerUuid);
        if (gifts != null && index >= 0 && index < gifts.size()) {
            gifts.remove(index);
            saveGifts();
        }
    }

    public void clearAllGifts(UUID playerUuid) {
        playerGifts.remove(playerUuid);
        saveGifts();
    }

    public boolean isInCooldown(String playerName) {
        if (giftCooldowns.containsKey(playerName)) {
            long cooldownTime = getConfig().getLong("cooldown-seconds", 60) * 1000;
            return System.currentTimeMillis() - giftCooldowns.get(playerName) < cooldownTime;
        }
        return false;
    }

    public void setCooldown(String playerName) {
        giftCooldowns.put(playerName, System.currentTimeMillis());
    }

    public Inventory createGiftGUI(Player player) {
        List<Gift> gifts = getPlayerGifts(player.getUniqueId());
        int size = Math.max(9, ((gifts.size() / 9) + 1) * 9);
        if (size > 54) size = 54;
        
        Inventory gui = Bukkit.createInventory(null, size, "§6Your Gifts");
        
        // Fill with glass panes
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < size; i++) {
            gui.setItem(i, filler);
        }
        
        // Add gifts
        for (int i = 0; i < gifts.size(); i++) {
            Gift gift = gifts.get(i);
            ItemStack item = gift.getItem().clone();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore();
                if (lore == null) lore = new ArrayList<>();
                lore.add("");
                lore.add("§7From: §e" + gift.getFromPlayer());
                lore.add("§7Date: §e" + new Date(gift.getTimestamp()));
                if (!gift.getMessage().isEmpty()) {
                    lore.add("");
                    lore.add("§7Message: §f" + gift.getMessage());
                }
                lore.add("");
                lore.add("§a§lClick to Claim");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            gui.setItem(i, item);
        }
        
        return gui;
    }

    public int getMaxGiftsPerSend() {
        return getConfig().getInt("max-gifts-per-send", 36);
    }

    public boolean isGiftingEnabled() {
        return getConfig().getBoolean("gifting-enabled", true);
    }
}
