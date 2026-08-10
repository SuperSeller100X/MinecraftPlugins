package com.example.coderewards;

import com.example.coderewards.commands.CodeCommand;
import com.example.coderewards.commands.CodeAdminCommand;
import com.example.coderewards.data.CodeManager;
import com.example.coderewards.gui.CodeGUI;
import com.example.coderewards.listeners.GUIListener;
import org.bukkit.plugin.java.JavaPlugin;

public class CodeRewardsPlugin extends JavaPlugin {

    private static CodeRewardsPlugin instance;
    private CodeManager codeManager;

    @Override
    public void onEnable() {
        instance = this;
        
        // Save default config
        saveDefaultConfig();
        
        // Initialize manager
        codeManager = new CodeManager(this);
        
        // Register commands
        getCommand("code").setExecutor(new CodeCommand(this));
        getCommand("codeadmin").setExecutor(new CodeAdminCommand(this));
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        
        getLogger().info("CodeRewards has been enabled!");
    }

    @Override
    public void onDisable() {
        codeManager.saveCodes();
        getLogger().info("CodeRewards has been disabled!");
    }

    public static CodeRewardsPlugin getInstance() {
        return instance;
    }

    public CodeManager getCodeManager() {
        return codeManager;
    }
}
