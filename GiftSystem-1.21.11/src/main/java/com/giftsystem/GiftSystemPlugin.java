package com.giftsystem;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Bukkit;
import java.util.HashMap;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public class GiftSystemPlugin extends JavaPlugin {
    
    private static GiftSystemPlugin instance;
    private FileConfiguration config;
    private HashMap<UUID, List<Gift>> pendingGifts;
    
    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        config = getConfig();
        
        pendingGifts = new HashMap<>();
        
        registerCommands();
        registerListeners();
        
        getLogger().info("GiftSystem has been enabled!");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("GiftSystem has been disabled!");
    }
    
    public static GiftSystemPlugin getInstance() {
        return instance;
    }
    
    public FileConfiguration getConfig() {
        return config;
    }
    
    public HashMap<UUID, List<Gift>> getPendingGifts() {
        return pendingGifts;
    }
    
    private void registerCommands() {
        GiftCommand giftCommand = new GiftCommand(this);
        GiftsCommand giftsCommand = new GiftsCommand(this);
        GiftGuiCommand giftGuiCommand = new GiftGuiCommand(this);
        GiftAdminCommand adminCommand = new GiftAdminCommand(this);
        
        getCommand("gift").setExecutor(giftCommand);
        getCommand("gifts").setExecutor(giftsCommand);
        getCommand("giftgui").setExecutor(giftGuiCommand);
        getCommand("giftadmin").setExecutor(adminCommand);
    }
    
    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new GiftListener(this), this);
    }
}
