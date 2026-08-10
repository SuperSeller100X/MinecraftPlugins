package com.plugins.giftsystem;

import com.plugins.giftsystem.commands.GiftAdminCommand;
import com.plugins.giftsystem.commands.GiftCommand;
import com.plugins.giftsystem.commands.GiftGuiCommand;
import com.plugins.giftsystem.commands.GiftsCommand;
import com.plugins.giftsystem.listeners.GiftListener;
import com.plugins.giftsystem.utils.DataManager;
import com.plugins.giftsystem.utils.GiftManager;
import org.bukkit.plugin.java.JavaPlugin;

public class GiftSystem extends JavaPlugin {

    private static GiftSystem instance;
    private GiftManager giftManager;
    private DataManager dataManager;

    @Override
    public void onEnable() {
        instance = this;
        
        // Save default config
        saveDefaultConfig();
        
        // Initialize managers
        dataManager = new DataManager(this);
        giftManager = new GiftManager(this);
        
        // Register commands
        getCommand("gift").setExecutor(new GiftCommand(this));
        getCommand("gift").setTabCompleter(new GiftCommand(this));
        getCommand("gifts").setExecutor(new GiftsCommand(this));
        getCommand("giftgui").setExecutor(new GiftGuiCommand(this));
        getCommand("giftadmin").setExecutor(new GiftAdminCommand(this));
        getCommand("giftadmin").setTabCompleter(new GiftAdminCommand(this));
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new GiftListener(this), this);
        
        getLogger().info("GiftSystem has been enabled!");
    }

    @Override
    public void onDisable() {
        if (dataManager != null) {
            dataManager.saveAll();
        }
        if (giftManager != null) {
            giftManager.saveGifts();
        }
        getLogger().info("GiftSystem has been disabled!");
    }

    public static GiftSystem getInstance() {
        return instance;
    }

    public GiftManager getGiftManager() {
        return giftManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }
}
