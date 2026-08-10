package com.plugins.coderewards;

import com.plugins.coderewards.commands.CodeAdminCommand;
import com.plugins.coderewards.commands.CodeRedeemCommand;
import com.plugins.coderewards.commands.CodeRewardsCommand;
import com.plugins.coderewards.listeners.PlayerListener;
import com.plugins.coderewards.utils.CodeManager;
import com.plugins.coderewards.utils.DataManager;
import org.bukkit.plugin.java.JavaPlugin;

public class CodeRewards extends JavaPlugin {

    private static CodeRewards instance;
    private CodeManager codeManager;
    private DataManager dataManager;

    @Override
    public void onEnable() {
        instance = this;
        
        // Save default config
        saveDefaultConfig();
        
        // Initialize managers
        dataManager = new DataManager(this);
        codeManager = new CodeManager(this);
        
        // Register commands
        getCommand("coderedeem").setExecutor(new CodeRedeemCommand(this));
        getCommand("coderedeem").setTabCompleter(new CodeRedeemCommand(this));
        getCommand("coderewards").setExecutor(new CodeRewardsCommand(this));
        getCommand("codeadmin").setExecutor(new CodeAdminCommand(this));
        getCommand("codeadmin").setTabCompleter(new CodeAdminCommand(this));
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        
        getLogger().info("CodeRewards has been enabled!");
    }

    @Override
    public void onDisable() {
        if (dataManager != null) {
            dataManager.saveAll();
        }
        if (codeManager != null) {
            codeManager.saveCodes();
        }
        getLogger().info("CodeRewards has been disabled!");
    }

    public static CodeRewards getInstance() {
        return instance;
    }

    public CodeManager getCodeManager() {
        return codeManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }
}
