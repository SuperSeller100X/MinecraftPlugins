package com.giftsystem.plugin;

import org.bukkit.inventory.ItemStack;

public class Gift {
    private String fromPlayer;
    private ItemStack item;
    private long timestamp;
    private String message;

    public Gift(String fromPlayer, ItemStack item, long timestamp, String message) {
        this.fromPlayer = fromPlayer;
        this.item = item;
        this.timestamp = timestamp;
        this.message = message;
    }

    public String getFromPlayer() {
        return fromPlayer;
    }

    public ItemStack getItem() {
        return item;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }
}
