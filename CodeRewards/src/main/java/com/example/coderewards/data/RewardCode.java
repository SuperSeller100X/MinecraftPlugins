package com.example.coderewards.data;

import org.bukkit.inventory.ItemStack;

import java.util.*;

public class RewardCode {
    private String code;
    private List<String> commands;
    private List<ItemStack> items;
    private int usesLeft;
    private int maxUses;
    private boolean oneTimePerPlayer;
    private Set<String> redeemedBy;

    public RewardCode(String code, List<String> commands, List<ItemStack> items, int maxUses, boolean oneTimePerPlayer) {
        this.code = code;
        this.commands = commands != null ? commands : new ArrayList<>();
        this.items = items != null ? items : new ArrayList<>();
        this.maxUses = maxUses;
        this.usesLeft = maxUses;
        this.oneTimePerPlayer = oneTimePerPlayer;
        this.redeemedBy = new HashSet<>();
    }

    public String getCode() {
        return code;
    }

    public List<String> getCommands() {
        return commands;
    }

    public List<ItemStack> getItems() {
        return items;
    }

    public int getUsesLeft() {
        return usesLeft;
    }

    public int getMaxUses() {
        return maxUses;
    }

    public boolean isOneTimePerPlayer() {
        return oneTimePerPlayer;
    }

    public Set<String> getRedeemedBy() {
        return redeemedBy;
    }

    public boolean canRedeem() {
        return usesLeft > 0;
    }

    public boolean hasRedeemed(String playerUUID) {
        return redeemedBy.contains(playerUUID);
    }

    public void redeem(String playerUUID) {
        if (oneTimePerPlayer && !redeemedBy.contains(playerUUID)) {
            redeemedBy.add(playerUUID);
        }
        usesLeft--;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("code", code);
        map.put("commands", commands);
        map.put("maxUses", maxUses);
        map.put("usesLeft", usesLeft);
        map.put("oneTimePerPlayer", oneTimePerPlayer);
        map.put("redeemedBy", new ArrayList<>(redeemedBy));
        
        // Serialize items
        List<Map<String, Object>> itemsData = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null) {
                itemsData.add(item.serialize());
            }
        }
        map.put("items", itemsData);
        
        return map;
    }

    public static RewardCode deserialize(Map<String, Object> map) {
        String code = (String) map.get("code");
        List<String> commands = (List<String>) map.get("commands");
        int maxUses = (int) map.getOrDefault("maxUses", -1);
        int usesLeft = (int) map.getOrDefault("usesLeft", maxUses);
        boolean oneTimePerPlayer = (boolean) map.getOrDefault("oneTimePerPlayer", true);
        List<String> redeemedByList = (List<String>) map.getOrDefault("redeemedBy", new ArrayList<>());
        
        List<ItemStack> items = new ArrayList<>();
        List<Map<String, Object>> itemsData = (List<Map<String, Object>>) map.get("items");
        if (itemsData != null) {
            for (Map<String, Object> itemData : itemsData) {
                try {
                    items.add(ItemStack.deserialize(itemData));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        RewardCode rewardCode = new RewardCode(code, commands, items, maxUses, oneTimePerPlayer);
        rewardCode.redeemedBy.addAll(redeemedByList);
        
        // Ensure usesLeft is consistent
        if (usesLeft < 0 || usesLeft > maxUses) {
            usesLeft = maxUses;
        }
        
        return rewardCode;
    }
}
