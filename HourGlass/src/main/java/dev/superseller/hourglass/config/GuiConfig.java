package dev.superseller.hourglass.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.superseller.hourglass.util.TimeFormat;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Typed view over {@code gui.yml}: every GUI title, size, icon, slot, text and
 * action comes from this file, so the whole interface can be re-skinned without
 * touching code.
 *
 * <p>Each item carries an {@code action:} string that binds it to plugin
 * behaviour (see {@link #ACTIONS}). An unknown action or material is reported in
 * the log and degraded to a harmless default rather than failing the load, so a
 * typo can never stop the server from starting.
 */
public final class GuiConfig {

    /** Every action a GUI item can trigger. */
    public static final Set<String> ACTIONS = Set.of(
            "none", "close", "back",
            "open-stats", "open-leaderboard", "open-milestones", "open-admin",
            "page-back", "page-forward", "page-first", "page-last", "page-self",
            "refresh", "freeze-toggle", "reset-time", "add-time", "remove-time",
            "view-player", "toggle-display", "copy-summary", "export", "reload");

    /** The GUI ids the plugin knows how to open. */
    public static final List<String> SCREENS = List.of("stats", "leaderboard", "milestones", "admin", "admin-player");

    /** One configured icon. */
    public record Item(
            String key,
            int slot,
            Material material,
            boolean playerHead,
            String name,
            List<String> lore,
            String action,
            long seconds,
            String target) {
    }

    /** One configured screen. */
    public record Screen(
            String id,
            String title,
            int size,
            boolean fill,
            Material fillerMaterial,
            String fillerName,
            Map<String, Item> items,
            int listStart,
            int listCells,
            Item listTemplate,
            boolean listHeads) {

        public Item item(String key) {
            return items.get(key);
        }

        /** First slot that list entries may occupy. */
        public int listStartOrDefault() {
            return listStart >= 0 ? listStart : 0;
        }

        /** How many slots the entry list may use, clamped to the screen. */
        public int listCellsForSize() {
            int start = listStartOrDefault();
            int room = size - start;
            if (listCells > 0) {
                return Math.min(listCells, room);
            }
            return Math.max(1, room);
        }
    }

    private final JavaPlugin plugin;
    private final Map<String, Screen> screens = new LinkedHashMap<>();

    public GuiConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Re-reads {@code gui.yml}. */
    public void load(YamlConfiguration yaml) {
        screens.clear();
        for (String id : SCREENS) {
            ConfigurationSection section = yaml.getConfigurationSection(id);
            screens.put(id, parse(id, section, yaml));
        }
    }

    public Screen screen(String id) {
        Screen screen = screens.get(id == null ? "stats" : id.toLowerCase(Locale.US));
        if (screen != null) {
            return screen;
        }
        Screen fallback = screens.get("stats");
        return fallback != null ? fallback
                : new Screen("stats", "<dark_gray>HourGlass", 27, true, Material.GRAY_STAINED_GLASS_PANE,
                        " ", Map.of(), -1, 0, null, false);
    }

    public Collection<Screen> all() {
        return screens.values();
    }

    public int screenCount() {
        return screens.size();
    }

    // ------------------------------------------------------------------ parse

    private Screen parse(String id, ConfigurationSection section, YamlConfiguration yaml) {
        String title = section == null ? defaultTitle(id) : section.getString("title", defaultTitle(id));
        boolean fill = section == null || section.getBoolean("fill", true);
        Material filler = material(section == null ? null : section.getString("filler-material",
                "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE, id + ".filler-material");
        String fillerName = section == null ? " " : section.getString("filler-name", " ");

        Map<String, Item> items = new LinkedHashMap<>();
        int maxSlot = 0;
        ConfigurationSection itemsSection = section == null ? null : section.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                Item item = parseItem(key, itemsSection.getConfigurationSection(key), id);
                if (item != null) {
                    items.put(key, item);
                    maxSlot = Math.max(maxSlot, item.slot());
                }
            }
        }

        int size = section == null ? 0 : section.getInt("size", 0);
        if (size <= 0) {
            size = roundUpToNine(maxSlot + 1);
        }
        size = Math.min(54, Math.max(9, roundUpToNine(size)));

        // Any icon placed beyond the configured size grows the GUI instead of silently vanishing.
        if (maxSlot >= size) {
            int grown = Math.min(54, roundUpToNine(maxSlot + 1));
            plugin.getLogger().warning("gui.yml: " + id + ".size is " + size
                    + " but an item sits in slot " + maxSlot + "; growing the GUI to " + grown + ".");
            size = grown;
        }

        ConfigurationSection list = section == null ? null : section.getConfigurationSection("list");
        int listStart = list == null ? -1 : list.getInt("start-slot", -1);
        int listCells = list == null ? 0 : Math.max(0, list.getInt("cells", 0));
        Item listTemplate = list == null ? null : parseItem("entry", list.getConfigurationSection("item"), id);
        boolean listHeads = list == null || list.getBoolean("player-heads", true);

        return new Screen(id, title, size, fill, filler, fillerName, Map.copyOf(items),
                listStart, listCells, listTemplate, listHeads);
    }

    private Item parseItem(String key, ConfigurationSection section, String screenId) {
        if (section == null) {
            return null;
        }
        int slot = Math.max(0, section.getInt("slot", 0));
        Material fallbackMaterial = "entry".equals(key) ? Material.PLAYER_HEAD : Material.STONE;
        Material material = material(section.getString("material", null), fallbackMaterial,
                screenId + ".items." + key + ".material");
        if ("entry".equals(key) && material == Material.STONE) {
            material = Material.PLAYER_HEAD;
        }
        boolean playerHead = section.getBoolean("player-head", material == Material.PLAYER_HEAD);
        String name = section.getString("name", key);
        List<String> lore = section.getStringList("lore");
        String action = section.getString("action", "none");
        if (action == null || action.isBlank()) {
            action = "none";
        }
        action = action.trim().toLowerCase(Locale.US);
        if (!ACTIONS.contains(action)) {
            plugin.getLogger().warning("gui.yml: " + screenId + "." + key + " uses unknown action '"
                    + action + "'. Supported: " + String.join(", ", new java.util.TreeSet<>(ACTIONS))
                    + " - the button does nothing until fixed.");
            action = "none";
        }
        long seconds = duration(section);
        String target = section.getString("target", null);
        return new Item(key, slot, material, playerHead, name, List.copyOf(lore), action, seconds, target);
    }

    /** {@code seconds:} or a human {@code amount: 1d} / {@code hours: 2}. */
    private static long duration(ConfigurationSection section) {
        if (section.contains("seconds")) {
            return Math.max(0L, section.getLong("seconds", 0L));
        }
        if (section.contains("hours")) {
            return Math.max(0L, (long) (section.getDouble("hours", 0d) * TimeFormat.HOUR));
        }
        if (section.contains("minutes")) {
            return Math.max(0L, (long) (section.getDouble("minutes", 0d) * TimeFormat.MINUTE));
        }
        if (section.contains("days")) {
            return Math.max(0L, (long) (section.getDouble("days", 0d) * TimeFormat.DAY));
        }
        String amount = section.getString("amount", null);
        return amount == null ? 0L : Math.max(0L, TimeFormat.parse(amount).orElse(0L));
    }

    private Material material(String configured, Material fallback, String path) {
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        Material found = Material.matchMaterial(configured.trim().toUpperCase(Locale.US));
        if (found == null || found.isAir() || !found.isItem()) {
            plugin.getLogger().warning("gui.yml: " + path + " = '" + configured
                    + "' is not an item material; using " + fallback.name() + ".");
            return fallback;
        }
        return found;
    }

    private static int roundUpToNine(int slots) {
        int rows = (slots + 8) / 9;
        return Math.max(1, Math.min(6, rows)) * 9;
    }

    /** Fallback titles for when gui.yml omits one (\u231B is the hourglass). */
    private static String defaultTitle(String id) {
        return switch (id) {
            case "leaderboard" -> "<dark_gray>\u231B <gray>Playtime Leaderboard";
            case "milestones" -> "<dark_gray>\u231B <gray>Your Milestones";
            case "admin" -> "<dark_gray>\u231B <red>HourGlass Admin";
            case "admin-player" -> "<dark_gray>\u231B <red>Player Details";
            default -> "<dark_gray>\u231B <gray>Your Playtime";
        };
    }
}
