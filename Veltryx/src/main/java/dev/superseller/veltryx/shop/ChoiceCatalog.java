package dev.superseller.veltryx.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Selectable shop extras: potions (choose the type), tipped arrows
 * (choose the effect) and enchanted books (choose the enchantment and its
 * level). Potion and arrow prices are derived from ingredient costs; book
 * prices depend on enchantment and level.
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
        out.add(new PotionOption("LEAPING", "Leaping", 55_00));
        out.add(new PotionOption("FIRE_RESISTANCE", "Fire Resistance", 60_00));
        out.add(new PotionOption("SWIFTNESS", "Swiftness", 55_00));
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
        // Brewable base, extended, and upgraded variants (LUCK is creative-only).
        for (String key : List.of("WATER", "MUNDANE", "THICK", "AWKWARD", "INFESTED", "OOZING",
                "WEAVING", "WIND_CHARGED", "LONG_FIRE_RESISTANCE", "LONG_INVISIBILITY",
                "LONG_LEAPING", "LONG_NIGHT_VISION", "LONG_POISON", "LONG_REGENERATION",
                "LONG_SLOW_FALLING", "LONG_SLOWNESS", "LONG_STRENGTH", "LONG_SWIFTNESS",
                "LONG_TURTLE_MASTER", "LONG_WATER_BREATHING", "LONG_WEAKNESS", "STRONG_HARMING",
                "STRONG_HEALING", "STRONG_LEAPING", "STRONG_POISON", "STRONG_REGENERATION",
                "STRONG_SLOWNESS", "STRONG_STRENGTH", "STRONG_SWIFTNESS", "STRONG_TURTLE_MASTER")) {
            out.add(new PotionOption(key, PriceTable.displayName(key), 65_00));
        }
        return out;
    }

    /** Eight-arrow pack size. */
    public static final int TIPPED_ARROW_PACK = 8;

    /** Legacy reference price; live prices use the current ingredient table. */
    public static long arrowPackCents(PotionOption potion) {
        return Math.max(1L, Math.round(potion.priceCents() * 1.25 / 5.0) * 5);
    }

    /** Enchantment book choices, in picker order. */
    public static List<BookOption> books() {
        List<BookOption> out = new ArrayList<>();
        // common: $10 per level
        out.add(new BookOption("PROTECTION", "Protection", 4, 10_00, "common"));
        out.add(new BookOption("FIRE_PROTECTION", "Fire Protection", 4, 10_00, "common"));
        out.add(new BookOption("BLAST_PROTECTION", "Blast Protection", 4, 10_00, "common"));
        out.add(new BookOption("PROJECTILE_PROTECTION", "Projectile Protection", 4, 10_00, "common"));
        out.add(new BookOption("FEATHER_FALLING", "Feather Falling", 4, 10_00, "common"));
        out.add(new BookOption("EFFICIENCY", "Efficiency", 5, 10_00, "common"));
        out.add(new BookOption("SHARPNESS", "Sharpness", 5, 10_00, "common"));
        out.add(new BookOption("SMITE", "Smite", 5, 10_00, "common"));
        out.add(new BookOption("BANE_OF_ARTHROPODS", "Bane of Arthropods", 5, 10_00, "common"));
        out.add(new BookOption("POWER", "Power", 5, 10_00, "common"));
        out.add(new BookOption("UNBREAKING", "Unbreaking", 3, 10_00, "common"));
        // uncommon: $20 per level
        out.add(new BookOption("THORNS", "Thorns", 3, 20_00, "uncommon"));
        out.add(new BookOption("RESPIRATION", "Respiration", 3, 20_00, "uncommon"));
        out.add(new BookOption("AQUA_AFFINITY", "Aqua Affinity", 1, 20_00, "uncommon"));
        out.add(new BookOption("DEPTH_STRIDER", "Depth Strider", 3, 20_00, "uncommon"));
        out.add(new BookOption("FORTUNE", "Fortune", 3, 20_00, "uncommon"));
        out.add(new BookOption("LOOTING", "Looting", 3, 20_00, "uncommon"));
        out.add(new BookOption("KNOCKBACK", "Knockback", 2, 20_00, "uncommon"));
        out.add(new BookOption("FIRE_ASPECT", "Fire Aspect", 2, 20_00, "uncommon"));
        out.add(new BookOption("PUNCH", "Punch", 2, 20_00, "uncommon"));
        out.add(new BookOption("FLAME", "Flame", 1, 20_00, "uncommon"));
        out.add(new BookOption("LURE", "Lure", 3, 20_00, "uncommon"));
        out.add(new BookOption("LUCK_OF_THE_SEA", "Luck of the Sea", 3, 20_00, "uncommon"));
        out.add(new BookOption("LOYALTY", "Loyalty", 3, 20_00, "uncommon"));
        out.add(new BookOption("IMPALING", "Impaling", 5, 20_00, "uncommon"));
        out.add(new BookOption("RIPTIDE", "Riptide", 3, 20_00, "uncommon"));
        out.add(new BookOption("CHANNELING", "Channeling", 1, 20_00, "uncommon"));
        // rare: $40 per level
        out.add(new BookOption("MENDING", "Mending", 1, 40_00, "rare"));
        out.add(new BookOption("SILK_TOUCH", "Silk Touch", 1, 40_00, "rare"));
        out.add(new BookOption("INFINITY", "Infinity", 1, 40_00, "rare"));
        out.add(new BookOption("BINDING_CURSE", "Curse of Binding", 1, 40_00, "rare"));
        out.add(new BookOption("VANISHING_CURSE", "Curse of Vanishing", 1, 40_00, "rare"));
        out.add(new BookOption("BREACH", "Breach", 4, 20_00, "uncommon"));
        out.add(new BookOption("DENSITY", "Density", 5, 20_00, "uncommon"));
        out.add(new BookOption("FROST_WALKER", "Frost Walker", 2, 20_00, "uncommon"));
        out.add(new BookOption("LUNGE", "Lunge", 3, 20_00, "uncommon"));
        out.add(new BookOption("MULTISHOT", "Multishot", 1, 20_00, "uncommon"));
        out.add(new BookOption("PIERCING", "Piercing", 4, 20_00, "uncommon"));
        out.add(new BookOption("QUICK_CHARGE", "Quick Charge", 3, 20_00, "uncommon"));
        out.add(new BookOption("SOUL_SPEED", "Soul Speed", 3, 40_00, "rare"));
        out.add(new BookOption("SWEEPING_EDGE", "Sweeping Edge", 3, 20_00, "uncommon"));
        out.add(new BookOption("SWIFT_SNEAK", "Swift Sneak", 3, 40_00, "rare"));
        out.add(new BookOption("WIND_BURST", "Wind Burst", 3, 40_00, "rare"));
        return out;
    }

    public static PotionOption potion(String key) {
        for (PotionOption option : potions()) if (option.key().equals(key)) return option;
        return null;
    }
    public static BookOption book(String key) {
        for (BookOption option : books()) if (option.key().equals(key)) return option;
        return null;
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
        String needle = query.toLowerCase(Locale.ROOT).trim().replace('_', ' ');
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
