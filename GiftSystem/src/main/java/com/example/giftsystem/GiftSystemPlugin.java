package com.example.giftsystem;

import com.example.giftsystem.commands.GiftCommand;
import com.example.giftsystem.commands.GiftAdminCommand;
import com.example.giftsystem.data.GiftManager;
import com.example.giftsystem.listeners.GiftListener;
import org.bukkit.plugin.java.JavaPlugin;

public class GiftSystemPlugin extends JavaPlugin {

    private static GiftSystemPlugin instance;
    private GiftManager giftManager;

    @Override
    public void onEnable() {
        instance = this;
        
        // Save default config
        saveDefaultConfig();
        
        // Initialize manager
        giftManager = new GiftManager(this);
        
        // Register commands
        getCommand("gift").setExecutor(new GiftCommand(this));
        getCommand("giftgui").setExecutor(new GiftCommand(this));
        getCommand("gifts").setExecutor(new GiftCommand(this));
        getCommand("giftadmin").setExecutor(new GiftAdminCommand(this));
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new GiftListener(this), this);
        
        getLogger().info("GiftSystem has been enabled!");
    }

    @Override
    public void onDisable() {
        giftManager.saveGifts();
        getLogger().info("GiftSystem has been disabled!");
    }

    public static GiftSystemPlugin getInstance() {
        return instance;
    }

    public GiftManager getGiftManager() {
        return giftManager;
    }
}
