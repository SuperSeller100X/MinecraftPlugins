package dev.superseller.subscriptions.util;

import java.util.Base64;

import org.bukkit.inventory.ItemStack;

/** Lossless Paper item serialization ({@code serializeAsBytes}). */
public final class ItemSerial {

    private ItemSerial() {
    }

    public static String encode(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "";
        }
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public static ItemStack decode(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static ItemStack cloneWithAmount(ItemStack template, int amount) {
        if (template == null) {
            return null;
        }
        ItemStack clone = template.clone();
        clone.setAmount(Math.max(1, Math.min(clone.getMaxStackSize(), amount)));
        return clone;
    }
}
