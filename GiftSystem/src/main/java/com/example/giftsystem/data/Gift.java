package com.example.giftsystem.data;

import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Gift {
    private String id;
    private String senderUUID;
    private String senderName;
    private String recipientUUID;
    private String recipientName;
    private List<ItemStack> items;
    private String message;
    private long timestamp;
    private boolean claimed;

    public Gift(String senderUUID, String senderName, String recipientUUID, String recipientName, 
                List<ItemStack> items, String message) {
        this.id = UUID.randomUUID().toString();
        this.senderUUID = senderUUID;
        this.senderName = senderName;
        this.recipientUUID = recipientUUID;
        this.recipientName = recipientName;
        this.items = items != null ? items : new ArrayList<>();
        this.message = message != null ? message : "";
        this.timestamp = System.currentTimeMillis();
        this.claimed = false;
    }

    public String getId() {
        return id;
    }

    public String getSenderUUID() {
        return senderUUID;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getRecipientUUID() {
        return recipientUUID;
    }

    public String getRecipientName() {
        return recipientName;
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

    public boolean isClaimed() {
        return claimed;
    }

    public void claim() {
        this.claimed = true;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("senderUUID", senderUUID);
        map.put("senderName", senderName);
        map.put("recipientUUID", recipientUUID);
        map.put("recipientName", recipientName);
        map.put("message", message);
        map.put("timestamp", timestamp);
        map.put("claimed", claimed);
        
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

    public static Gift deserialize(Map<String, Object> map) {
        String id = (String) map.get("id");
        String senderUUID = (String) map.get("senderUUID");
        String senderName = (String) map.get("senderName");
        String recipientUUID = (String) map.get("recipientUUID");
        String recipientName = (String) map.get("recipientName");
        String message = (String) map.getOrDefault("message", "");
        long timestamp = ((Number) map.getOrDefault("timestamp", System.currentTimeMillis())).longValue();
        boolean claimed = (boolean) map.getOrDefault("claimed", false);
        
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

        Gift gift = new Gift(senderUUID, senderName, recipientUUID, recipientName, items, message);
        // Override generated values with saved ones
        try {
            java.lang.reflect.Field idField = Gift.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(gift, id);
            
            java.lang.reflect.Field timestampField = Gift.class.getDeclaredField("timestamp");
            timestampField.setAccessible(true);
            timestampField.setLong(gift, timestamp);
            
            java.lang.reflect.Field claimedField = Gift.class.getDeclaredField("claimed");
            claimedField.setAccessible(true);
            claimedField.setBoolean(gift, claimed);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return gift;
    }
}
