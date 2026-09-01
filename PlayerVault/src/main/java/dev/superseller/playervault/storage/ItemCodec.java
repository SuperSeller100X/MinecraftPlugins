package dev.superseller.playervault.storage;

import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Converts vault contents to and from a single Base64 string.
 *
 * <p>Backed by Paper's NBT item serialisation
 * ({@link ItemStack#serializeItemsAsBytes(ItemStack[])}) rather than Bukkit's
 * {@code ConfigurationSerializable} map format, because NBT round-trips through the
 * vanilla data converter and therefore survives Minecraft version upgrades safely.
 */
public final class ItemCodec {

    private ItemCodec() {
    }

    /**
     * Serialises a whole vault into one Base64 string.
     *
     * @param items vault contents; {@code null} entries are stored as empty slots
     * @return the encoded string, or an empty string for a completely empty vault
     */
    public static String encode(ItemStack[] items) {
        if (items == null || items.length == 0) {
            return "";
        }
        ItemStack[] safe = new ItemStack[items.length];
        boolean any = false;
        for (int i = 0; i < items.length; i++) {
            ItemStack stack = items[i];
            if (stack != null && stack.getType() != Material.AIR) {
                safe[i] = stack;
                any = true;
            }
        }
        if (!any) {
            return "";
        }
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(safe));
    }

    /**
     * Restores a vault from its Base64 string.
     *
     * @param encoded the stored string; {@code null} or blank yields an empty vault
     * @param size the number of slots the vault currently has
     * @param logger used to report corrupt data without throwing
     * @return an array of exactly {@code size} slots, empty slots being {@code null}
     */
    public static ItemStack[] decode(String encoded, int size, Logger logger) {
        ItemStack[] result = new ItemStack[Math.max(0, size)];
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException ex) {
            if (logger != null) {
                logger.log(Level.WARNING, "Stored vault data is not valid Base64; starting empty.", ex);
            }
            return result;
        }
        if (bytes.length == 0) {
            return result;
        }
        ItemStack[] loaded;
        try {
            loaded = ItemStack.deserializeItemsFromBytes(bytes);
        } catch (RuntimeException ex) {
            if (logger != null) {
                logger.log(Level.WARNING, "Stored vault data could not be read; starting empty.", ex);
            }
            return result;
        }
        if (loaded == null) {
            return result;
        }
        for (int i = 0; i < Math.min(loaded.length, result.length); i++) {
            ItemStack stack = loaded[i];
            if (stack != null && stack.getType() != Material.AIR && stack.getAmount() > 0) {
                result[i] = stack;
            }
        }
        return result;
    }
}
