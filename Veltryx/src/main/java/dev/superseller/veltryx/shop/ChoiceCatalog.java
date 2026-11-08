package dev.superseller.veltryx.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The configurable shop extras: potions (choose the type), tipped arrows
 * (choose the effect) and enchanted books (choose the enchantment and its
 * level). Prices are plain tables so the smoke suite can verify them; the
 * items they describe are never /sell-able, so no buy-sell loop can exist.
 */
public final class ChoiceCatalog {

    /** One selectable potion / tipped-arrow effect. */
    public record PotionOption(String key, String displayName, long priceCents) { }

    /** One selectable enchantment for an enchanted book. */
    public record BookOption(String key, String displayName, int maxLevel, long pricePerLevelCents, String tier) { }

    private ChoiceCatalog() {
    }

    /** Potion effect choices, in picker order. */
    public static List<PotionOption> potions() {
        List<PotionOption> out = new ArrayList<>();
        out.add(new PotionOption("NIGHT_VISION", "Night Vision", 45_00));
        out.add(new PotionOption("INVISIBILITY", "Invisibility", 90_00));
        out.add(new PotionOption("JUMP_BOOST", "Leaping", 55_00));
        out.add(new PotionOption("FIRE_RESISTANCE", "Fire Resistance", 60_00));
        out.add(new PotionOption("SPEED", "Swiftness", 55_00));
        out.add(new PotionOption("SLOWNESS", "Slowness", 35_00));
        out.add(new PotionOption("WATER_BREATHING", "Water Breathing", 50_00));
        out.add(new PotionOption("HEALING", "Healing", 50_00));
        out.add(new PotionOption("HARMING", "Harming", 55_00));
        out.add(new PotionOption("POISON", "Poison", 50_00));
        out.add(new PotionOption("REGENERATION", "Regeneration", 75_00));
        out.add(new PotionOption("STRENGTH", "Strength", 70_00));
        out.add(new PotionOption("WEAKNESS", "Weakness", 35_00));
        out.add(new PotionOption("TURTLE_MASTER", "Turtle Master", 85_00));
        out.add(new PotionOption("SLOW_FALLING", "Slow Falling", 55_00));
        return out;
    }

    /** Tipped arrows are sold in packs of 8 at 1.25x the matching potion. */
    public static final int TIPPED_ARROW_PACK = 8;

    /** Price of one tipped-arrow pack for a potion option. */
    public static long arrowPackCents(PotionOption potion) {
        return Math.max(1L, Math.round(potion.priceCents() * 1.25 / 5.0) * 5);
    }

    /** Enchantment book choices, in picker order. */
    public static List<BookOption> books() {
        List<BookOption> out = new ArrayList<>();
        // common: 250 per level
        out.add(new BookOption("PROTECTION", "Protection", 4, 250_00, "common"));
        out.add(new BookOption("FIRE_PROTECTION", "Fire Protection", 4, 250_00, "common"));
        out.add(new BookOption("BLAST_PROTECTION", "Blast Protection", 4, 250_00, "common"));
        out.add(new BookOption("PROJECTILE_PROTECTION", "Projectile Protection", 4, 250_00, "common"));
        out.add(new BookOption("FEATHER_FALLING", "Feather Falling", 4, 250_00, "common"));
        out.add(new BookOption("EFFICIENCY", "Efficiency", 5, 250_00, "common"));
        out.add(new BookOption("SHARPNESS", "Sharpness", 5, 250_00, "common"));
        out.add(new BookOption("SMITE", "Smite", 5, 250_00, "common"));
        out.add(new BookOption("BANE_OF_ARTHROPODS", "Bane of Arthropods", 5, 250_00, "common"));
        out.add(new BookOption("POWER", "Power", 5, 250_00, "common"));
        out.add(new BookOption("UNBREAKING", "Unbreaking", 3, 250_00, "common"));
        // uncommon: 450 per level
        out.add(new BookOption("THORNS", "Thorns", 3, 450_00, "uncommon"));
        out.add(new BookOption("RESPIRATION", "Respiration", 3, 450_00, "uncommon"));
        out.add(new BookOption("AQUA_AFFINITY", "Aqua Affinity", 1, 450_00, "uncommon"));
        out.add(new BookOption("DEPTH_STRIDER", "Depth Strider", 3, 450_00, "uncommon"));
        out.add(new BookOption("FORTUNE", "Fortune", 3, 450_00, "uncommon"));
        out.add(new BookOption("LOOTING", "Looting", 3, 450_00, "uncommon"));
        out.add(new BookOption("KNOCKBACK", "Knockback", 2, 450_00, "uncommon"));
        out.add(new BookOption("FIRE_ASPECT", "Fire Aspect", 2, 450_00, "uncommon"));
        out.add(new BookOption("PUNCH", "Punch", 2, 450_00, "uncommon"));
        out.add(new BookOption("FLAME", "Flame", 1, 450_00, "uncommon"));
        out.add(new BookOption("LURE", "Lure", 3, 450_00, "uncommon"));
        out.add(new BookOption("LUCK_OF_THE_SEA", "Luck of the Sea", 3, 450_00, "uncommon"));
        out.add(new BookOption("LOYALTY", "Loyalty", 3, 450_00, "uncommon"));
        out.add(new BookOption("IMPALING", "Impaling", 5, 450_00, "uncommon"));
        out.add(new BookOption("RIPTIDE", "Riptide", 3, 450_00, "uncommon"));
        out.add(new BookOption("CHANNELING", "Channeling", 1, 450_00, "uncommon"));
        // rare: 800 per level
        out.add(new BookOption("MENDING", "Mending", 1, 800_00, "rare"));
        out.add(new BookOption("SILK_TOUCH", "Silk Touch", 1, 800_00, "rare"));
        out.add(new BookOption("INFINITY", "Infinity", 1, 800_00, "rare"));
        return out;
    }

    /** Price of one enchanted book for a given option and level. */
    public static long bookPriceCents(BookOption option, int level) {
        int clamped = Math.max(1, Math.min(level, option.maxLevel()));
        return option.pricePerLevelCents() * clamped;
    }

    /** Case-insensitive "does the item match the query" for the shop search. */
    public static boolean matches(String query, String displayName, String materialName) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String needle = query.toLowerCase(Locale.ROOT).trim();
        if (displayName != null && displayName.toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        return materialName != null && materialName.toLowerCase(Locale.ROOT).replace('_', ' ').contains(needle);
    }

    /** Default quick-shop item list (material names, up to 45). */
    public static List<String> defaultQuickshopItems() {
        return List.of(
                "STONE", "COBBLESTONE", "DEEPSLATE", "COBBLED_DEEPSLATE", "DIRT",
                "GRASS_BLOCK", "SAND", "GRAVEL", "GLASS", "BRICKS",
                "OAK_LOG", "OAK_PLANKS", "SPRUCE_PLANKS", "COBBLESTONE_WALL", "LADDER",
                "TORCH", "CHEST", "BARREL", "IRON_INGOT", "GOLD_INGOT",
                "COPPER_INGOT", "REDSTONE", "LAPIS_LAZULI", "DIAMOND", "EMERALD",
                "COAL", "IRON_BLOCK", "GOLD_BLOCK", "DIAMOND_BLOCK", "OBSIDIAN",
                "IRON_PICKAXE", "DIAMOND_PICKAXE", "IRON_SWORD", "DIAMOND_SWORD", "BOW",
                "ARROW", "SHIELD", "IRON_HELMET", "IRON_CHESTPLATE", "DIAMOND_HELMET",
                "DIAMOND_CHESTPLATE", "COOKED_BEEF", "BREAD", "GOLDEN_APPLE", "ENDER_PEARL");
    }
}
