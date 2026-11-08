package dev.superseller.veltryx.shards;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.enchantments.Enchantment;

/**
 * The shard shop catalog: the shard weapons and tools. Prices are balanced
 * against the passive income of 1 shard/minute (60/hour): a weapon or tool
 * costs 500 shards (~8 hours of play) and self-destructs 24 real-time hours
 * after purchase, exactly like the ShardTools plugin. The void totem is a
 * cheaper backup at 150 shards. Buy-with-money shard purchasing is
 * deliberately NOT part of this catalog - shards are earned by playing,
 * paid between players or given by staff.
 */
public final class ShardCatalog {

    /** One purchasable shard item. */
    public static final class Entry {
        private final String id;
        private final String material;
        private final String displayName;
        private final long priceShards;
        private final long lifetimeMs;
        private final Behavior behavior;
        private final List<EnchantSpec> enchants;
        private final List<EnchantSpec> choice; // pick-one alternatives (Fortune III vs Silk Touch)
        private final List<String> lore;

        Entry(String id, String material, String displayName, long priceShards, long lifetimeMs,
              Behavior behavior, List<EnchantSpec> enchants, List<EnchantSpec> choice, List<String> lore) {
            this.id = id;
            this.material = material;
            this.displayName = displayName;
            this.priceShards = priceShards;
            this.lifetimeMs = lifetimeMs;
            this.behavior = behavior;
            this.enchants = enchants;
            this.choice = choice;
            this.lore = lore;
        }

        public String id() { return id; }
        public String material() { return material; }
        public String displayName() { return displayName; }
        public long priceShards() { return priceShards; }
        public long lifetimeMs() { return lifetimeMs; }
        public boolean expires() { return lifetimeMs > 0L; }
        public Behavior behavior() { return behavior; }
        public List<EnchantSpec> enchants() { return enchants; }
        public List<EnchantSpec> choice() { return choice; }
        public boolean hasChoice() { return !choice.isEmpty(); }
        public List<String> lore() { return lore; }

        /** The pick-one alternative at {@code index} (0 = first, e.g. Fortune). */
        public EnchantSpec choice(int index) {
            return choice.isEmpty() ? null : choice.get(Math.max(0, Math.min(index, choice.size() - 1)));
        }
    }

    /** One enchantment line on a shard item. */
    public record EnchantSpec(Enchantment enchantment, int level, String displayName) { }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public ShardCatalog() {
        // The four weapons/tools cost 500 shards each (~8h of play at 1/min),
        // the void totem 150. All tools self-destruct 24h after purchase.
        entries.put("shard_spear", new Entry("shard_spear", "NETHERITE_SWORD",
                "Shard Spear", 500, 24L * 60 * 60 * 1000,
                Behavior.LUNGE_SPEAR,
                List.of(new EnchantSpec(Enchantment.SHARPNESS, 5, "Sharpness V"),
                        new EnchantSpec(Enchantment.UNBREAKING, 3, "Unbreaking III"),
                        new EnchantSpec(Enchantment.MENDING, 1, "Mending")),
                List.of(),
                List.of("Right-click to lunge forward",
                        "90% less hunger drain while lunging",
                        "Self-destructs 24h after purchase")));
        entries.put("shard_trident", new Entry("shard_trident", "TRIDENT",
                "Shard Trident", 500, 24L * 60 * 60 * 1000,
                Behavior.RIPTIDE_TRIDENT,
                List.of(new EnchantSpec(Enchantment.RIPTIDE, 3, "Riptide III"),
                        new EnchantSpec(Enchantment.UNBREAKING, 3, "Unbreaking III"),
                        new EnchantSpec(Enchantment.MENDING, 1, "Mending")),
                List.of(),
                List.of("Riptide works without water",
                        "3 second cooldown",
                        "Self-destructs 24h after purchase")));
        entries.put("shard_axe", new Entry("shard_axe", "NETHERITE_AXE",
                "Shard Axe", 500, 24L * 60 * 60 * 1000,
                Behavior.TREE_AXE,
                List.of(new EnchantSpec(Enchantment.EFFICIENCY, 5, "Efficiency V"),
                        new EnchantSpec(Enchantment.UNBREAKING, 3, "Unbreaking III"),
                        new EnchantSpec(Enchantment.MENDING, 1, "Mending")),
                List.of(new EnchantSpec(Enchantment.FORTUNE, 3, "Fortune III"),
                        new EnchantSpec(Enchantment.SILK_TOUCH, 1, "Silk Touch")),
                List.of("Fells whole trees at once (max 128 logs)",
                        "3x3 on everything else",
                        "Self-destructs 24h after purchase")));
        entries.put("shard_pickaxe", new Entry("shard_pickaxe", "NETHERITE_PICKAXE",
                "Shard Pickaxe", 500, 24L * 60 * 60 * 1000,
                Behavior.AREA_MINE,
                List.of(new EnchantSpec(Enchantment.EFFICIENCY, 5, "Efficiency V"),
                        new EnchantSpec(Enchantment.UNBREAKING, 3, "Unbreaking III"),
                        new EnchantSpec(Enchantment.MENDING, 1, "Mending")),
                List.of(new EnchantSpec(Enchantment.FORTUNE, 3, "Fortune III"),
                        new EnchantSpec(Enchantment.SILK_TOUCH, 1, "Silk Touch")),
                List.of("Mines a 3x3 plane",
                        "Amethyst particles on all blocks mined",
                        "Self-destructs 24h after purchase")));
        entries.put("void_totem", new Entry("void_totem", "TOTEM_OF_UNDYING",
                "Void Totem", 150, 0,
                Behavior.VOID_TOTEM,
                List.of(),
                List.of(),
                List.of("Rescues you from a lethal void hit")));
    }

    public Entry byId(String id) {
        return id == null ? null : entries.get(id);
    }

    public List<Entry> all() {
        return new ArrayList<>(entries.values());
    }
}
