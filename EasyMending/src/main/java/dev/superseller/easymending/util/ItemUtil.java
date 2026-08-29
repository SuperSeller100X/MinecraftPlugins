package dev.superseller.easymending.util;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Utility functions for inspecting and modifying item durability and enchantments.
 */
public final class ItemUtil {

    private ItemUtil() {
    }

    /**
     * Determines whether an item stack can take damage and be repaired.
     *
     * @param item item to inspect
     * @return true if the item is damageable and breakable
     */
    public static boolean isRepairable(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (item.getType().getMaxDurability() <= 0) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return false;
        }
        return !damageable.isUnbreakable();
    }

    /**
     * Checks whether an item possesses the Mending enchantment.
     *
     * @param item item to inspect
     * @return true if enchanted with Mending
     */
    public static boolean hasMending(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.hasEnchant(Enchantment.MENDING);
    }

    /**
     * Gets the missing durability (damage) on an item.
     *
     * @param item item to inspect
     * @return current damage, or 0 if item cannot be damaged
     */
    public static int getDamage(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            return Math.max(0, damageable.getDamage());
        }
        return 0;
    }

    /**
     * Gets the maximum durability of an item.
     *
     * @param item item to inspect
     * @return max durability, or 0 if not damageable
     */
    public static int getMaxDurability(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        return item.getType().getMaxDurability();
    }

    /**
     * Gets the current remaining durability points of an item.
     *
     * @param item item to inspect
     * @return remaining durability points
     */
    public static int getRemainingDurability(ItemStack item) {
        int max = getMaxDurability(item);
        if (max <= 0) {
            return 0;
        }
        return Math.max(0, max - getDamage(item));
    }

    /**
     * Repairs an item by the specified amount of durability points.
     *
     * @param item item to repair
     * @param amount durability points to restore
     * @return actual durability points restored
     */
    public static int repair(ItemStack item, int amount) {
        if (amount <= 0 || !isRepairable(item)) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return 0;
        }
        int currentDamage = damageable.getDamage();
        if (currentDamage <= 0) {
            return 0;
        }
        int restore = Math.min(currentDamage, amount);
        damageable.setDamage(currentDamage - restore);
        item.setItemMeta(damageable);
        return restore;
    }

    /**
     * Formats a clean human-readable name for an item stack.
     *
     * @param item item to format
     * @return formatted name
     */
    public static String getFriendlyName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "Air";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component displayName = meta.displayName();
            if (displayName != null) {
                String plain = PlainTextComponentSerializer.plainText().serialize(displayName);
                if (!plain.isBlank()) {
                    return plain;
                }
            }
            String legacy = meta.getDisplayName();
            if (legacy != null && !legacy.isBlank()) {
                return legacy.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
            }
        }
        return formatMaterialName(item.getType());
    }

    /**
     * Converts a Material enum name into a title-cased name (e.g. DIAMOND_PICKAXE -> Diamond Pickaxe).
     *
     * @param material material to format
     * @return clean title-cased name
     */
    public static String formatMaterialName(Material material) {
        if (material == null) {
            return "Unknown";
        }
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
