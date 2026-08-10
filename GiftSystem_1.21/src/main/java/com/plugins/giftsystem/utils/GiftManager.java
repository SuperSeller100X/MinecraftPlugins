package com.plugins.giftsystem.utils;

import com.plugins.giftsystem.GiftSystem;
import com.plugins.giftsystem.models.Gift;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GiftManager {
    private final GiftSystem plugin;
    private final Map<String, List<Gift>> playerGifts;
    private final File giftsFile;
    private FileConfiguration giftsConfig;

    public GiftManager(GiftSystem plugin) {
        this.plugin = plugin;
        this.playerGifts = new ConcurrentHashMap<>();
        this.giftsFile = new File(plugin.getDataFolder(), "gifts.yml");
        loadGifts();
    }

    public void loadGifts() {
        if (!giftsFile.exists()) {
            try {
                giftsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create gifts.yml: " + e.getMessage());
                return;
            }
        }

        giftsConfig = YamlConfiguration.loadConfiguration(giftsFile);
        playerGifts.clear();

        ConfigurationSection section = giftsConfig.getConfigurationSection("gifts");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection giftSection = section.getConfigurationSection(key);
                if (giftSection != null) {
                    Gift gift = Gift.fromConfigSection(giftSection);
                    if (gift != null) {
                        String recipient = gift.getRecipientName().toLowerCase();
                        playerGifts.computeIfAbsent(recipient, k -> new ArrayList<>()).add(gift);
                    }
                }
            }
        }

        // Clean up expired gifts
        cleanupExpiredGifts();

        plugin.getLogger().info("Loaded gifts for " + playerGifts.size() + " players.");
    }

    public void saveGifts() {
        if (giftsConfig == null) {
            giftsConfig = new YamlConfiguration();
        }

        giftsConfig.set("gifts", null);
        
        int index = 0;
        for (List<Gift> gifts : playerGifts.values()) {
            for (Gift gift : gifts) {
                String path = "gifts.gift" + index;
                gift.toConfigSection(giftsConfig, path);
                index++;
            }
        }

        try {
            giftsConfig.save(giftsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save gifts.yml: " + e.getMessage());
        }
    }

    public void addGift(Gift gift) {
        String recipient = gift.getRecipientName().toLowerCase();
        playerGifts.computeIfAbsent(recipient, k -> new ArrayList<>()).add(gift);
        saveGifts();
    }

    public List<Gift> getPlayerGifts(String playerName) {
        List<Gift> gifts = playerGifts.get(playerName.toLowerCase());
        if (gifts == null) {
            return new ArrayList<>();
        }
        // Return only available gifts
        List<Gift> available = new ArrayList<>();
        for (Gift gift : gifts) {
            if (gift.isAvailable()) {
                available.add(gift);
            }
        }
        return available;
    }

    public int getPendingGiftCount(String playerName) {
        return getPlayerGifts(playerName).size();
    }

    public boolean openGift(UUID giftId, String playerName) {
        List<Gift> gifts = playerGifts.get(playerName.toLowerCase());
        if (gifts == null) return false;

        for (Gift gift : gifts) {
            if (gift.getId().equals(giftId) && gift.isAvailable()) {
                gift.setOpened(true);
                saveGifts();
                return true;
            }
        }
        return false;
    }

    public Gift getGift(UUID giftId, String playerName) {
        List<Gift> gifts = playerGifts.get(playerName.toLowerCase());
        if (gifts == null) return null;

        for (Gift gift : gifts) {
            if (gift.getId().equals(giftId)) {
                return gift;
            }
        }
        return null;
    }

    public void clearPlayerGifts(String playerName) {
        playerGifts.remove(playerName.toLowerCase());
        saveGifts();
    }

    public void cleanupExpiredGifts() {
        boolean changed = false;
        
        for (List<Gift> gifts : playerGifts.values()) {
            Iterator<Gift> iterator = gifts.iterator();
            while (iterator.hasNext()) {
                Gift gift = iterator.next();
                if (gift.isExpired() || gift.isOpened()) {
                    iterator.remove();
                    changed = true;
                }
            }
        }
        
        if (changed) {
            saveGifts();
        }
    }

    public int getTotalGifts() {
        int total = 0;
        for (List<Gift> gifts : playerGifts.values()) {
            total += gifts.size();
        }
        return total;
    }

    public int getPendingGiftsCount() {
        int total = 0;
        for (List<Gift> gifts : playerGifts.values()) {
            for (Gift gift : gifts) {
                if (gift.isAvailable()) {
                    total++;
                }
            }
        }
        return total;
    }
}
