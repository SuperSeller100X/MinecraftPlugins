package com.giftsystem;

import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import java.util.UUID;
import java.util.List;

public class Gift {
    private UUID sender;
    private String senderName;
    private List<ItemStack> items;
    private String message;
    private long timestamp;
    
    public Gift(UUID sender, String senderName, List<ItemStack> items, String message) {
        this.sender = sender;
        this.senderName = senderName;
        this.items = items;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
    
    public UUID getSender() {
        return sender;
    }
    
    public String getSenderName() {
        return senderName;
    }
    
    public List<ItemStack> getItems() {
        return items;
    }
    
    public String getMessage() {
        return message;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
}
