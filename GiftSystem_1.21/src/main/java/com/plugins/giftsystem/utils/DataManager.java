package com.plugins.giftsystem.utils;

import com.plugins.giftsystem.GiftSystem;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DataManager {
    private final GiftSystem plugin;
    private final Map<String, PlayerGiftData> playerDataMap;
    private final File dataFolder;

    public DataManager(GiftSystem plugin) {
        this.plugin = plugin;
        this.playerDataMap = new HashMap<>();
        this.dataFolder = new File(plugin.getDataFolder(), "players");
        
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        loadAllData();
    }

    public void loadAllData() {
        playerDataMap.clear();
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        
        if (files != null) {
            for (File file : files) {
                try {
                    FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                    String playerName = file.getName().replace(".yml", "");
                    
                    PlayerGiftData data = new PlayerGiftData(playerName);
                    data.setLastSendTime(config.getLong("lastSendTime", 0));
                    data.setDailySentCount(config.getInt("dailySentCount", 0));
                    data.setDailyResetTime(config.getLong("dailyResetTime", System.currentTimeMillis()));
                    
                    playerDataMap.put(playerName.toLowerCase(), data);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load player data: " + file.getName());
                }
            }
        }
        
        plugin.getLogger().info("Loaded data for " + playerDataMap.size() + " players.");
    }

    public void saveAll() {
        for (PlayerGiftData data : playerDataMap.values()) {
            savePlayerData(data);
        }
    }

    public void savePlayerData(PlayerGiftData data) {
        File file = new File(dataFolder, data.getPlayerName() + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        config.set("playerName", data.getPlayerName());
        config.set("lastSendTime", data.getLastSendTime());
        config.set("dailySentCount", data.getDailySentCount());
        config.set("dailyResetTime", data.getDailyResetTime());
        
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save player data for " + data.getPlayerName() + ": " + e.getMessage());
        }
    }

    public PlayerGiftData getPlayerData(String playerName) {
        return playerDataMap.computeIfAbsent(playerName.toLowerCase(), k -> new PlayerGiftData(playerName));
    }

    public boolean hasPlayerData(String playerName) {
        return playerDataMap.containsKey(playerName.toLowerCase());
    }

    public void removePlayerData(String playerName) {
        PlayerGiftData data = playerDataMap.remove(playerName.toLowerCase());
        if (data != null) {
            File file = new File(dataFolder, playerName + ".yml");
            if (file.exists()) {
                file.delete();
            }
        }
    }

    public int getTotalPlayers() {
        return playerDataMap.size();
    }

    public static class PlayerGiftData {
        private final String playerName;
        private long lastSendTime;
        private int dailySentCount;
        private long dailyResetTime;

        public PlayerGiftData(String playerName) {
            this.playerName = playerName;
            this.lastSendTime = 0;
            this.dailySentCount = 0;
            this.dailyResetTime = System.currentTimeMillis();
        }

        public String getPlayerName() {
            return playerName;
        }

        public long getLastSendTime() {
            return lastSendTime;
        }

        public void setLastSendTime(long time) {
            this.lastSendTime = time;
        }

        public int getDailySentCount() {
            checkDailyReset();
            return dailySentCount;
        }

        public void setDailySentCount(int count) {
            this.dailySentCount = count;
        }

        public void incrementDailySentCount() {
            checkDailyReset();
            dailySentCount++;
        }

        public long getDailyResetTime() {
            return dailyResetTime;
        }

        public void setDailyResetTime(long time) {
            this.dailyResetTime = time;
        }

        private void checkDailyReset() {
            long currentTime = System.currentTimeMillis();
            if (currentTime - dailyResetTime > 86400000) { // 24 hours
                dailySentCount = 0;
                dailyResetTime = currentTime;
            }
        }
    }
}
