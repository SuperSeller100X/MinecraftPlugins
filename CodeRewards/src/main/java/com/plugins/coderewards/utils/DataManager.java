package com.plugins.coderewards.utils;

import com.plugins.coderewards.CodeRewards;
import com.plugins.coderewards.models.PlayerData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DataManager {
    private final CodeRewards plugin;
    private final Map<UUID, PlayerData> playerDataMap;
    private final File dataFolder;

    public DataManager(CodeRewards plugin) {
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
                    UUID uuid = UUID.fromString(file.getName().replace(".yml", ""));
                    String playerName = config.getString("playerName", "Unknown");
                    
                    PlayerData data = new PlayerData(uuid, playerName);
                    Set<String> redeemedCodes = config.getStringList("redeemedCodes") != null ? 
                        new HashSet<>(config.getStringList("redeemedCodes")) : new HashSet<>();
                    data.getRedeemedCodes().addAll(redeemedCodes);
                    data.setLastRedemptionTime(config.getLong("lastRedemptionTime", 0));
                    data.setDailyResetTime(config.getLong("dailyResetTime", System.currentTimeMillis()));
                    
                    playerDataMap.put(uuid, data);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load player data: " + file.getName());
                }
            }
        }
        
        plugin.getLogger().info("Loaded data for " + playerDataMap.size() + " players.");
    }

    public void saveAll() {
        for (PlayerData data : playerDataMap.values()) {
            savePlayerData(data);
        }
    }

    public void savePlayerData(PlayerData data) {
        File file = new File(dataFolder, data.getUuid() + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        config.set("playerName", data.getPlayerName());
        config.set("redeemedCodes", data.getRedeemedCodes());
        config.set("lastRedemptionTime", data.getLastRedemptionTime());
        config.set("dailyRedemptions", data.getDailyRedemptions());
        config.set("dailyResetTime", data.getDailyResetTime()); // Need to add getter
        
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save player data for " + data.getPlayerName() + ": " + e.getMessage());
        }
    }

    public PlayerData getPlayerData(UUID uuid, String playerName) {
        return playerDataMap.computeIfAbsent(uuid, k -> new PlayerData(uuid, playerName));
    }

    public boolean hasPlayerData(UUID uuid) {
        return playerDataMap.containsKey(uuid);
    }

    public void removePlayerData(UUID uuid) {
        PlayerData data = playerDataMap.remove(uuid);
        if (data != null) {
            File file = new File(dataFolder, uuid + ".yml");
            if (file.exists()) {
                file.delete();
            }
        }
    }

    public int getTotalPlayers() {
        return playerDataMap.size();
    }
}
