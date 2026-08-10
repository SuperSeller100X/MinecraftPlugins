package com.plugins.coderewards.models;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerData {
    private UUID uuid;
    private String playerName;
    private Set<String> redeemedCodes;
    private int dailyRedemptions;
    private long lastRedemptionTime;
    private long dailyResetTime;

    public PlayerData(UUID uuid, String playerName) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.redeemedCodes = new HashSet<>();
        this.dailyRedemptions = 0;
        this.lastRedemptionTime = 0;
        this.dailyResetTime = System.currentTimeMillis();
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Set<String> getRedeemedCodes() {
        return redeemedCodes;
    }

    public boolean hasRedeemedCode(String code) {
        return redeemedCodes.contains(code.toUpperCase());
    }

    public void addRedeemedCode(String code) {
        redeemedCodes.add(code.toUpperCase());
    }

    public int getDailyRedemptions() {
        checkDailyReset();
        return dailyRedemptions;
    }

    public void incrementDailyRedemptions() {
        checkDailyReset();
        dailyRedemptions++;
    }

    public long getLastRedemptionTime() {
        return lastRedemptionTime;
    }

    public void setLastRedemptionTime(long time) {
        this.lastRedemptionTime = time;
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
            dailyRedemptions = 0;
            dailyResetTime = currentTime;
        }
    }

    public void resetDaily() {
        dailyRedemptions = 0;
        dailyResetTime = System.currentTimeMillis();
    }
}
