package com.plugins.giftsystem.models;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class Gift {
    private UUID id;
    private String senderName;
    private UUID senderUuid;
    private String recipientName;
    private UUID recipientUuid;
    private List<ItemStack> items;
    private String message;
    private long createdAt;
    private long expiresAt;
    private boolean opened;

    public Gift(String senderName, UUID senderUuid, String recipientName, UUID recipientUuid, 
                List<ItemStack> items, String message, long expirationHours) {
        this.id = UUID.randomUUID();
        this.senderName = senderName;
        this.senderUuid = senderUuid;
        this.recipientName = recipientName;
        this.recipientUuid = recipientUuid;
        this.items = items;
        this.message = message != null ? message : "";
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = expirationHours > 0 ? 
            this.createdAt + (expirationHours * 3600 * 1000) : 0;
        this.opened = false;
    }

    public UUID getId() {
        return id;
    }

    public String getSenderName() {
        return senderName;
    }

    public UUID getSenderUuid() {
        return senderUuid;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public UUID getRecipientUuid() {
        return recipientUuid;
    }

    public List<ItemStack> getItems() {
        return items;
    }

    public String getMessage() {
        return message;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return expiresAt > 0 && System.currentTimeMillis() >= expiresAt;
    }

    public boolean isOpened() {
        return opened;
    }

    public void setOpened(boolean opened) {
        this.opened = opened;
    }

    public boolean isAvailable() {
        return !opened && !isExpired();
    }

    public String serializeItems() {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            
            dataOutput.writeInt(items.size());
            
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize items", e);
        }
    }

    public static List<ItemStack> deserializeItems(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            
            int size = dataInput.readInt();
            List<ItemStack> items = new ArrayList<>();
            
            for (int i = 0; i < size; i++) {
                items.add((ItemStack) dataInput.readObject());
            }
            
            dataInput.close();
            return items;
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize items", e);
        }
    }

    public ConfigurationSection toConfigSection(FileConfiguration config, String path) {
        ConfigurationSection section = config.createSection(path);
        section.set("id", id.toString());
        section.set("senderName", senderName);
        section.set("senderUuid", senderUuid.toString());
        section.set("recipientName", recipientName);
        section.set("recipientUuid", recipientUuid.toString());
        section.set("items", serializeItems());
        section.set("message", message);
        section.set("createdAt", createdAt);
        section.set("expiresAt", expiresAt);
        section.set("opened", opened);
        return section;
    }

    public static Gift fromConfigSection(ConfigurationSection section) {
        if (section == null) return null;
        
        try {
            UUID id = UUID.fromString(section.getString("id"));
            String senderName = section.getString("senderName");
            UUID senderUuid = UUID.fromString(section.getString("senderUuid"));
            String recipientName = section.getString("recipientName");
            UUID recipientUuid = UUID.fromString(section.getString("recipientUuid"));
            List<ItemStack> items = deserializeItems(section.getString("items"));
            String message = section.getString("message", "");
            long createdAt = section.getLong("createdAt", System.currentTimeMillis());
            long expiresAt = section.getLong("expiresAt", 0);
            boolean opened = section.getBoolean("opened", false);
            
            Gift gift = new Gift(senderName, senderUuid, recipientName, recipientUuid, 
                                items, message, 0);
            gift.id = id;
            gift.createdAt = createdAt;
            gift.expiresAt = expiresAt;
            gift.opened = opened;
            
            return gift;
        } catch (Exception e) {
            return null;
        }
    }
}
