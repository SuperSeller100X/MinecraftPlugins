package com.example.giftsystem.data;

import com.example.giftsystem.GiftSystemPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class GiftManager {
    private final GiftSystemPlugin plugin;
    private final Map<String, List<Gift>> playerGifts;
    private File giftsFile;
    private YamlConfiguration giftsConfig;

    public GiftManager(GiftSystemPlugin plugin) {
        this.plugin = plugin;
        this.playerGifts = new HashMap<>();
        loadGifts();
    }

    public void loadGifts() {
        giftsFile = new File(plugin.getDataFolder(), "gifts.yml");
        if (!giftsFile.exists()) {
            plugin.saveResource("gifts.yml", false);
        }
        giftsConfig = YamlConfiguration.loadConfiguration(giftsFile);
        
        playerGifts.clear();
        ConfigurationSection section = giftsConfig.getConfigurationSection("gifts");
        if (section != null) {
            for (String playerUUID : section.getKeys(false)) {
                ConfigurationSection playerSection = section.getConfigurationSection(playerUUID);
                if (playerSection != null) {
                    List<Gift> gifts = new ArrayList<>();
                    for (String giftKey : playerSection.getKeys(false)) {
                        ConfigurationSection giftSection = playerSection.getConfigurationSection(giftKey);
                        if (giftSection != null) {
                            try {
                                Map<String, Object> giftData = giftSection.getValues(false);
                                Map<String, Object> serializedMap = new HashMap<>();
                                for (Map.Entry<String, Object> entry : giftData.entrySet()) {
                                    if (entry.getValue() instanceof ConfigurationSection) {
                                        serializedMap.put(entry.getKey(), ((ConfigurationSection) entry.getValue()).getValues(false));
                                    } else if (entry.getValue() instanceof List) {
                                        List<?> list = (List<?>) entry.getValue();
                                        List<Map<String, Object>> complexList = new ArrayList<>();
                                        boolean hasSections = false;
                                        for (Object obj : list) {
                                            if (obj instanceof ConfigurationSection) {
                                                complexList.add(((ConfigurationSection) obj).getValues(false));
                                                hasSections = true;
                                            }
                                        }
                                        if (hasSections) {
                                            serializedMap.put(entry.getKey(), complexList);
                                        } else {
                                            serializedMap.put(entry.getKey(), (List<String>) list);
                                        }
                                    } else {
                                        serializedMap.put(entry.getKey(), entry.getValue());
                                    }
                                }
                                Gift gift = Gift.deserialize(serializedMap);
                                gifts.add(gift);
                            } catch (Exception e) {
                                plugin.getLogger().warning("Failed to load gift: " + giftKey);
                                e.printStackTrace();
                            }
                        }
                    }
                    playerGifts.put(playerUUID, gifts);
                }
            }
        }
        plugin.getLogger().info("Loaded gifts for " + playerGifts.size() + " players.");
    }

    public void saveGifts() {
        if (giftsConfig == null) {
            giftsConfig = new YamlConfiguration();
        }
        
        giftsConfig.set("gifts", null);
        for (Map.Entry<String, List<Gift>> entry : playerGifts.entrySet()) {
            String playerUUID = entry.getKey();
            List<Gift> gifts = entry.getValue();
            
            for (int i = 0; i < gifts.size(); i++) {
                Gift gift = gifts.get(i);
                String path = "gifts." + playerUUID + ".gift" + i;
                Map<String, Object> serialized = gift.serialize();
                for (Map.Entry<String, Object> dataEntry : serialized.entrySet()) {
                    giftsConfig.set(path + "." + dataEntry.getKey(), dataEntry.getValue());
                }
            }
        }
        
        try {
            giftsConfig.save(giftsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save gifts.yml!");
            e.printStackTrace();
        }
    }

    public void addGift(Gift gift) {
        String recipientUUID = gift.getRecipientUUID();
        playerGifts.computeIfAbsent(recipientUUID, k -> new ArrayList<>()).add(gift);
        saveGifts();
    }

    public List<Gift> getGifts(String playerUUID) {
        return playerGifts.getOrDefault(playerUUID, new ArrayList<>());
    }

    public List<Gift> getUnclaimedGifts(String playerUUID) {
        List<Gift> unclaimed = new ArrayList<>();
        for (Gift gift : getGifts(playerUUID)) {
            if (!gift.isClaimed()) {
                unclaimed.add(gift);
            }
        }
        return unclaimed;
    }

    public void removeGift(String playerUUID, String giftId) {
        List<Gift> gifts = playerGifts.get(playerUUID);
        if (gifts != null) {
            gifts.removeIf(gift -> gift.getId().equals(giftId));
            saveGifts();
        }
    }

    public void clearPlayerGifts(String playerUUID) {
        playerGifts.remove(playerUUID);
        saveGifts();
    }

    public int getPendingGiftCount(String playerUUID) {
        return getUnclaimedGifts(playerUUID).size();
    }
}
