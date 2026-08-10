package com.coderewards;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Bukkit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class CodeRewardsPlugin extends JavaPlugin {
    
    private static CodeRewardsPlugin instance;
    private FileConfiguration config;
    private HashMap<String, Code> activeCodes;
    private Set<String> redeemedCodes;
    
    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        config = getConfig();
        
        activeCodes = new HashMap<>();
        redeemedCodes = new HashSet<>();
        
        loadCodesFromConfig();
        registerCommands();
        registerListeners();
        
        getLogger().info("CodeRewards has been enabled!");
    }
    
    @Override
    public void onDisable() {
        saveCodesToConfig();
        getLogger().info("CodeRewards has been disabled!");
    }
    
    public static CodeRewardsPlugin getInstance() {
        return instance;
    }
    
    public FileConfiguration getConfig() {
        return config;
    }
    
    public HashMap<String, Code> getActiveCodes() {
        return activeCodes;
    }
    
    public Set<String> getRedeemedCodes() {
        return redeemedCodes;
    }
    
    private void loadCodesFromConfig() {
        if (config.contains("codes")) {
            for (String code : config.getConfigurationSection("codes").getKeys(false)) {
                String rewardType = config.getString("codes." + code + ".rewardType");
                double amount = config.getDouble("codes." + code + ".amount");
                boolean used = config.getBoolean("codes." + code + ".used", false);
                
                Code codeObj = new Code(code, rewardType, amount, !used);
                activeCodes.put(code, codeObj);
            }
        }
    }
    
    private void saveCodesToConfig() {
        config.set("codes", null);
        for (String codeStr : activeCodes.keySet()) {
            Code code = activeCodes.get(codeStr);
            config.set("codes." + codeStr + ".rewardType", code.getRewardType());
            config.set("codes." + codeStr + ".amount", code.getAmount());
            config.set("codes." + codeStr + ".used", !code.isActive());
        }
        saveConfig();
    }
    
    private void registerCommands() {
        RedeemCommand redeemCommand = new RedeemCommand(this);
        CodesCommand codesCommand = new CodesCommand(this);
        CodeAdminCommand adminCommand = new CodeAdminCommand(this);
        
        getCommand("redeem").setExecutor(redeemCommand);
        getCommand("codes").setExecutor(codesCommand);
        getCommand("codeadmin").setExecutor(adminCommand);
    }
    
    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new CodeListener(this), this);
    }
}
