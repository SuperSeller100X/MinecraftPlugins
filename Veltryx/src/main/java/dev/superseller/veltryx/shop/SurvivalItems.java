package dev.superseller.veltryx.shop;

import java.util.Map;
import java.util.Set;

/** Items which cannot normally be obtained in Java survival (26.3). */
public final class SurvivalItems {
    private static final Set<String> EXCLUDED = Set.of(
            "AIR", "ELYTRA", "DRAGON_EGG", "PLAYER_HEAD", "BARRIER", "BEDROCK", "LIGHT",
            "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART",
            "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "KNOWLEDGE_BOOK", "DEBUG_STICK",
            "TEST_BLOCK", "TEST_INSTANCE_BLOCK", "END_PORTAL_FRAME", "BUDDING_AMETHYST",
            "CHORUS_PLANT", "DIRT_PATH", "FARMLAND", "FROGSPAWN", "REINFORCED_DEEPSLATE",
            "SPAWNER", "SUSPICIOUS_SAND", "SUSPICIOUS_GRAVEL", "TRIAL_SPAWNER", "VAULT",
            "TALL_GRASS", "LARGE_FERN", "PETRIFIED_OAK_SLAB");
    // Preserve prices from old shop.yml files after the Mojang material renames.
    private static final Map<String, String> RENAMES = Map.of(
            "CHAIN", "IRON_CHAIN", "CHEST_BAMBOO_RAFT", "BAMBOO_CHEST_RAFT",
            "CRIMSON_WOOD", "CRIMSON_HYPHAE", "STRIPPED_CRIMSON_WOOD", "STRIPPED_CRIMSON_HYPHAE",
            "WARPED_WOOD", "WARPED_HYPHAE", "STRIPPED_WARPED_WOOD", "STRIPPED_WARPED_HYPHAE",
            "ANGLED_POTTERY_SHERD", "ANGLER_POTTERY_SHERD", "OMINOUS_KEY", "OMINOUS_TRIAL_KEY");
    private SurvivalItems() { }
    public static String canonical(String name) { return RENAMES.getOrDefault(name, name); }
    public static boolean allowed(String name) {
        return name != null && !EXCLUDED.contains(name)
                && !name.endsWith("_SPAWN_EGG") && !name.startsWith("INFESTED_");
    }
    /** Previous defaults for these items were buy-only; recognize only exact old prices. */
    public static long migrateOldSellDefault(String material, long buy, long sell) {
        if (sell != 0) return sell;
        if (buy == 3_500 && (material.equals("POTION") || material.equals("SPLASH_POTION")
                || material.equals("LINGERING_POTION"))) return 4;
        if (buy == 500 && material.equals("TIPPED_ARROW")) return 2;
        if (buy == 25_000 && material.equals("ENCHANTED_BOOK")) return 38;
        if (buy == 1_000 && material.equals("WRITTEN_BOOK")) return 50;
        if (buy == 15_000 && material.endsWith("_MAP")) return 50;
        return sell;
    }

    /** A conservative ingredient/blank-copy cap for variant and clonable items. */
    public static String saleCostBase(String material) {
        return switch (material) {
            case "POTION", "SPLASH_POTION", "LINGERING_POTION" -> "GLASS_BOTTLE";
            case "TIPPED_ARROW" -> "ARROW";
            case "ENCHANTED_BOOK" -> "BOOK";
            case "WRITTEN_BOOK" -> "WRITABLE_BOOK";
            default -> material.endsWith("_MAP") ? "MAP" : material;
        };
    }
    public static boolean needsChoice(String material) {
        return material.equals("POTION") || material.equals("SPLASH_POTION")
                || material.equals("LINGERING_POTION") || material.equals("TIPPED_ARROW")
                || material.equals("ENCHANTED_BOOK");
    }
}
