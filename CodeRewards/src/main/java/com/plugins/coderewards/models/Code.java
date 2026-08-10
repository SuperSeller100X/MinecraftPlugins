package com.plugins.coderewards.models;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class Code {
    private String code;
    private List<String> rewards;
    private String rewardType; // money, items, commands, xp
    private int uses;
    private int maxUses;
    private long expiresAt;
    private boolean active;

    public Code(String code, List<String> rewards, String rewardType, int maxUses, long expiresAt) {
        this.code = code.toUpperCase();
        this.rewards = rewards;
        this.rewardType = rewardType;
        this.uses = 0;
        this.maxUses = maxUses;
        this.expiresAt = expiresAt;
        this.active = true;
    }

    public String getCode() {
        return code;
    }

    public List<String> getRewards() {
        return rewards;
    }

    public String getRewardType() {
        return rewardType;
    }

    public int getUses() {
        return uses;
    }

    public void incrementUses() {
        this.uses++;
    }

    public int getMaxUses() {
        return maxUses;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isActive() {
        return active && (maxUses <= 0 || uses < maxUses) && (expiresAt <= 0 || System.currentTimeMillis() < expiresAt);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isExpired() {
        return expiresAt > 0 && System.currentTimeMillis() >= expiresAt;
    }

    public boolean isMaxUsesReached() {
        return maxUses > 0 && uses >= maxUses;
    }

    public ConfigurationSection toConfigSection(FileConfiguration config, String path) {
        ConfigurationSection section = config.createSection(path);
        section.set("code", code);
        section.set("rewards", rewards);
        section.set("rewardType", rewardType);
        section.set("maxUses", maxUses);
        section.set("uses", uses);
        section.set("expiresAt", expiresAt);
        section.set("active", active);
        return section;
    }

    public static Code fromConfigSection(ConfigurationSection section) {
        if (section == null) return null;
        
        String code = section.getString("code");
        List<String> rewards = section.getStringList("rewards");
        String rewardType = section.getString("rewardType", "commands");
        int maxUses = section.getInt("maxUses", -1);
        int uses = section.getInt("uses", 0);
        long expiresAt = section.getLong("expiresAt", 0);
        boolean active = section.getBoolean("active", true);
        
        Code codeObj = new Code(code, rewards, rewardType, maxUses, expiresAt);
        codeObj.uses = uses;
        codeObj.active = active;
        
        return codeObj;
    }
}
