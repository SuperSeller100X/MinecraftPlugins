package dev.superseller.shardtools.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * The shop catalog loaded from the "items:" section of config.yml.
 * Every entry is fully configurable - materials, names, enchants, prices
 * and lifetimes can all be changed, added or removed.
 */
public final class ShardCatalog {

    /** One purchasable item. */
    public static final class Entry {

        private final String id;
        private final String material;
        private final String displayName;
        private final long defaultPrice;
        private final long lifetimeMs;
        private final Behavior behavior;
        private final List<EnchantSpec> enchants;
        private final List<String> lore;
        private final String command;

        public Entry(String id, String material, String displayName, long defaultPrice,
                     long lifetimeMs, Behavior behavior, List<EnchantSpec> enchants, List<String> lore) {
            this(id, material, displayName, defaultPrice, lifetimeMs, behavior, enchants, lore, null);
        }

        public Entry(String id, String material, String displayName, long defaultPrice,
                     long lifetimeMs, Behavior behavior, List<EnchantSpec> enchants, List<String> lore,
                     String command) {
            this.id = id;
            this.material = material;
            this.displayName = displayName;
            this.defaultPrice = defaultPrice;
            this.lifetimeMs = lifetimeMs;
            this.behavior = behavior;
            this.enchants = enchants;
            this.lore = lore;
            this.command = command;
        }

        public String id() {
            return id;
        }

        public String material() {
            return material;
        }

        public String displayName() {
            return displayName;
        }

        public long defaultPrice() {
            return defaultPrice;
        }

        public long lifetimeMs() {
            return lifetimeMs;
        }

        public Behavior behavior() {
            return behavior;
        }

        public List<EnchantSpec> enchants() {
            return enchants;
        }

        public List<String> lore() {
            return lore;
        }

        public boolean expires() {
            return lifetimeMs > 0L;
        }

        /** Console command executed on purchase instead of giving an item (%player%). */
        public String command() {
            return command;
        }

        public boolean isCommandItem() {
            return command != null && !command.isBlank();
        }
    }

    private final Map<String, Entry> byId = new LinkedHashMap<>();
    private final List<Entry> ordered = new ArrayList<>();

    public static ShardCatalog load(FileConfiguration config, Logger logger) {
        ShardCatalog catalog = new ShardCatalog();
        ConfigurationSection root = config.getConfigurationSection("items");
        if (root == null) {
            logger.warning("No items: section found in config.yml - the shop will be empty");
            return catalog;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            String material = section.getString("material", "STONE");
            String name = section.getString("name", id);
            long price = Math.max(0L, section.getLong("price", 0L));
            long lifetimeHours = Math.max(0L, section.getLong("lifetime-hours", 0L));
            Behavior behavior = Behavior.parse(section.getString("behavior", "NONE"));
            List<EnchantSpec> enchants = new ArrayList<>();
            for (String spec : section.getStringList("enchants")) {
                EnchantSpec parsed = EnchantSpec.parse(spec);
                if (parsed != null) {
                    enchants.add(parsed);
                }
            }
            List<String> lore = new ArrayList<>(section.getStringList("lore"));
            String command = section.getString("command");
            if (command != null && command.isBlank()) {
                command = null;
            }
            Entry entry = new Entry(id.toLowerCase(Locale.ROOT), material.toUpperCase(Locale.ROOT),
                    name, price, lifetimeHours * 3_600_000L, behavior, enchants, lore, command);
            catalog.byId.put(entry.id(), entry);
            catalog.ordered.add(entry);
        }
        logger.info("Loaded " + catalog.ordered.size() + " shop items");
        return catalog;
    }

    public Entry byId(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Entry> ordered() {
        return Collections.unmodifiableList(ordered);
    }

    public Map<String, Long> defaultPrices() {
        Map<String, Long> prices = new LinkedHashMap<>();
        for (Entry entry : ordered) {
            prices.put(entry.id(), entry.defaultPrice());
        }
        return prices;
    }
}
