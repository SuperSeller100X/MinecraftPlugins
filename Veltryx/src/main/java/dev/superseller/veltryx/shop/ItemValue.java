package dev.superseller.veltryx.shop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * How much {@code /sell} pays for items that carry enchantments or have
 * lost durability:
 *
 * <ul>
 *   <li><b>Enchantments raise the price</b>: every total enchant level adds
 *       12% of the material's flat sell price (a fully enchanted tool sells
 *       for much more than a plain one).</li>
 *   <li><b>Lost durability lowers the price</b>: the value scales with the
 *       remaining durability (a broken tool is worth almost nothing).</li>
 *   <li>The result is always strictly below the shop buy price, so a
 *       buy-enchant-sell loop can never print money.</li>
 * </ul>
 *
 * Pure math (plus tiny Bukkit reads) so the smoke suite can verify it.
 */
public final class ItemValue {

    /** Each total enchant level raises the sell value by 12% of its base. */
    public static final double ENCHANT_BONUS_PER_LEVEL = 0.12;

    private ItemValue() {
    }

    /**
     * Sell value in cents of one item.
     *
     * @param sellBaseCents   the flat /sell price of the plain material
     * @param buyCents        the shop buy price of the plain material (cap)
     * @param enchantLevels   total enchantment levels on the item
     * @param remainingRatio  remaining durability ratio in [0, 1]
     */
    public static long valueCents(long sellBaseCents, long buyCents, int enchantLevels, double remainingRatio) {
        if (sellBaseCents <= 0) {
            return 0;
        }
        double enchanted = sellBaseCents * (1.0 + ENCHANT_BONUS_PER_LEVEL * Math.max(0, enchantLevels));
        double ratio = Math.max(0.0, Math.min(1.0, remainingRatio));
        long value = Math.round(enchanted * ratio);
        if (buyCents > 0) {
            // strict loop safety: never pay out what the shop charges
            value = Math.min(value, buyCents - 1);
            // The base sell price is at most half of the cheapest recipe cost;
            // cap the enchantment bonus below twice that amount too.
            long bonusCap = sellBaseCents > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : 2 * sellBaseCents - 1;
            value = Math.min(value, bonusCap);
        }
        return Math.max(0, value);
    }

    /** Sum of all enchantment levels on a stack (0 for unenchanted items). */
    public static int enchantLevels(ItemStack stack) {
        if (stack == null) {
            return 0;
        }
        long levels = 0;
        for (int level : stack.getEnchantments().values()) levels += Math.max(0, level);
        if (stack.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta book) {
            for (int level : book.getStoredEnchants().values()) levels += Math.max(0, level);
        }
        return (int) Math.min(Integer.MAX_VALUE, levels);
    }

    /**
     * Remaining durability ratio in [0, 1]. Items without durability (or
     * full ones) return 1.0; a broken tool approaches 0.
     */
    public static double remainingRatio(ItemStack stack) {
        if (stack == null) {
            return 1.0;
        }
        Material material = stack.getType();
        int max = material == null ? 0 : material.getMaxDurability();
        int damage = 0;
        if (stack.getItemMeta() instanceof Damageable damageable) {
            if (damageable.hasMaxDamage()) max = damageable.getMaxDamage();
            damage = damageable.getDamage();
        }
        if (max <= 0) return 1.0;
        double remaining = ((double) max - damage) / max;
        return Math.max(0.0, Math.min(1.0, remaining));
    }

    /** The full {@code /sell} value of one stack item given its material prices. */
    public static long valueCents(long sellBaseCents, long buyCents, ItemStack stack) {
        return valueCents(sellBaseCents, buyCents, enchantLevels(stack), remainingRatio(stack));
    }

    /** True when the stack carries enchantments or has lost durability. */
    public static boolean isValued(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        if (enchantLevels(stack) > 0) {
            return true;
        }
        return stack.getItemMeta() instanceof Damageable damageable && damageable.getDamage() > 0;
    }
}
