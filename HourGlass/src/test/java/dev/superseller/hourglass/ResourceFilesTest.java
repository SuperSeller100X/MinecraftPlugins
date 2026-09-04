package dev.superseller.hourglass;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.superseller.hourglass.config.GuiConfig;
import dev.superseller.hourglass.sound.SoundService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads the shipped {@code config.yml}, {@code messages.yml} and {@code gui.yml}
 * exactly as they land in the jar and verifies them against the code. These are
 * the mistakes that only show up once somebody types the command on a live
 * server — a message key with a typo, a GUI slot outside the inventory, an
 * action nobody implements — so they are checked at build time instead.
 */
class ResourceFilesTest {

    /** Dotted literals in Java sources that are deliberately not message keys. */
    private static final Set<String> NOT_MESSAGE_KEYS = Set.of("java.version", "config.yml", "messages.yml",
            "gui.yml", "players.yml");

    private static final Pattern LITERAL = Pattern.compile("\"([a-z][a-z0-9]*(?:\\.[a-z0-9\\-]+)+)\"");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_\\-]*)");

    private static YamlConfiguration resource(String name) throws Exception {
        try (Reader reader = new InputStreamReader(
                ResourceFilesTest.class.getResourceAsStream("/" + name), StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(reader);
            return yaml;
        }
    }

    // ------------------------------------------------------------- messages.yml

    @Test
    @DisplayName("every message key used in the sources exists in messages.yml")
    void messageKeysExist() throws Exception {
        Path sources = Path.of("src/main/java/dev/superseller/hourglass");
        Assumptions.assumeTrue(Files.isDirectory(sources), "run from the module directory to scan sources");
        YamlConfiguration messages = resource("messages.yml");
        Set<String> keys = new HashSet<>();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sources)) {
            walk.filter(path -> path.getFileName().toString().endsWith(".java"))
                    // Only the packages that talk to players; config / storage classes
                    // hold raw YAML paths that are not message keys.
                    .filter(path -> {
                        String relative = sources.relativize(path).toString().replace('\\', '/');
                        return relative.startsWith("command/") || relative.startsWith("gui/")
                                || relative.startsWith("listener/") || relative.startsWith("service/")
                                || relative.startsWith("api/") || relative.startsWith("integration/");
                    })
                    .forEach(files::add);
        }
        assertFalse(files.isEmpty());
        for (Path file : files) {
            Matcher matcher = LITERAL.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (matcher.find()) {
                String key = matcher.group(1);
                if (!NOT_MESSAGE_KEYS.contains(key) && !key.startsWith("hourglass.") && !key.endsWith(".yml")) {
                    keys.add(key);
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            if (!messages.isString(key)) {
                missing.add(key);
            }
        }
        assertTrue(keys.size() > 40, "the scan should find plenty of keys, found " + keys.size());
        assertTrue(missing.isEmpty(), "messages.yml is missing: " + missing);
    }

    @Test
    @DisplayName("messages are strings, never lists or sections")
    void messageValuesAreStrings() throws Exception {
        YamlConfiguration messages = resource("messages.yml");
        for (String key : Set.of("prefix", "stats.total", "leaderboard.header", "history.line", "admin.stats",
                "help.player", "milestones.reached", "gui.reset-confirm", "join.summary")) {
            assertTrue(messages.isString(key), key + " must be a single string (use \\n for lines)");
            assertFalse(messages.getString(key).isBlank(), key + " must not be empty here");
        }
        assertTrue(messages.getBoolean("legacy-color-codes"), "legacy codes are on by default");
        assertTrue(messages.getBoolean("escape-placeholder-values"), "player names are escaped by default");
    }

    // ------------------------------------------------------------------ gui.yml

    @Test
    @DisplayName("every screen is a legal inventory with legal slots")
    void guiScreens() throws Exception {
        YamlConfiguration gui = resource("gui.yml");
        for (String screen : GuiConfig.SCREENS) {
            ConfigurationSection section = gui.getConfigurationSection(screen);
            assertNotNull(section, "gui.yml must define the '" + screen + "' screen");
            int size = section.getInt("size", 27);
            assertTrue(size % 9 == 0 && size >= 9 && size <= 54, screen + ".size must be 9..54 in rows: " + size);
            assertFalse(section.getString("title", "").isBlank(), screen + " needs a title");

            Set<Integer> taken = new HashSet<>();
            ConfigurationSection items = section.getConfigurationSection("items");
            assertNotNull(items, screen + " needs items");
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                assertNotNull(item, screen + ".items." + key);
                int slot = item.getInt("slot", -1);
                assertTrue(slot >= 0 && slot < size, screen + "." + key + " slot " + slot + " is outside size " + size);
                assertTrue(taken.add(slot), screen + ": slot " + slot + " is used twice (" + key + ")");
                assertMaterial(screen + "." + key + ".material", item.getString("material"));
                assertAction(screen + "." + key + ".action", item.getString("action"));
                assertPlaceholders(screen + "." + key, item);
            }
            ConfigurationSection list = section.getConfigurationSection("list");
            if (list != null) {
                int start = list.getInt("start-slot", 0);
                int cells = list.getInt("cells", 0);
                assertTrue(start >= 0 && start < size, screen + ".list.start-slot must be inside the GUI");
                assertTrue(cells > 0 && start + cells <= size,
                        screen + ".list needs room: start " + start + " + cells " + cells + " > " + size);
                ConfigurationSection template = list.getConfigurationSection("item");
                assertNotNull(template, screen + ".list.item must exist");
                assertAction(screen + ".list.item.action", template.getString("action"));
                assertMaterial(screen + ".list.item.material", template.getString("material"));
                assertPlaceholders(screen + ".list.item", template);
            }
            assertMaterial(screen + ".filler-material", section.getString("filler-material"));
        }
    }

    @Test
    @DisplayName("add / remove buttons carry a real duration")
    void guiDurations() throws Exception {
        YamlConfiguration gui = resource("gui.yml");
        int editButtons = 0;
        for (String screen : GuiConfig.SCREENS) {
            ConfigurationSection items = gui.getConfigurationSection(screen + ".items");
            if (items == null) {
                continue;
            }
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                String action = item.getString("action", "none");
                if (!action.equals("add-time") && !action.equals("remove-time")) {
                    continue;
                }
                editButtons++;
                long seconds = item.getLong("seconds", 0L) + item.getLong("amount", 0L)
                        + item.getLong("hours", 0L) * 3_600L + item.getLong("days", 0L) * 86_400L
                        + item.getLong("minutes", 0L) * 60L + item.getLong("weeks", 0L) * 604_800L;
                assertTrue(seconds > 0L, screen + "." + key + " shifts no time at all");
                assertTrue(seconds <= 10L * 365 * 86_400L, screen + "." + key + " shifts an absurd amount");
            }
        }
        assertTrue(editButtons >= 2, "the admin GUI is expected to offer add and remove buttons");
    }

    // --------------------------------------------------------------- config.yml

    @Test
    @DisplayName("config.yml covers every switch the code reads")
    void configPaths() throws Exception {
        YamlConfiguration config = resource("config.yml");
        List<String> required = List.of("config-version", "debug", "general.primary-metric", "general.time-zone",
                "general.date-format", "storage.autosave-minutes", "storage.history-size", "storage.max-players",
                "tracking.idle-detection", "tracking.idle-seconds", "leaderboard.enabled", "leaderboard.per-page",
                "leaderboard.refresh-seconds", "display.mode", "display.update-seconds", "display.bossbar.color",
                "join.summary-enabled", "quit.staff-broadcast", "milestones.enabled", "gui.fill",
                "gui.progress-bar.length", "export.directory", "export.line-endings", "purge.default-days",
                "placeholders.enabled", "commands.allow-console", "sounds.enabled", "admin.reset-keeps-first-join",
                "format.style",
                "format.units.second");
        List<String> missing = new ArrayList<>();
        for (String path : required) {
            if (!config.contains(path)) {
                missing.add(path);
            }
        }
        assertTrue(missing.isEmpty(), "config.yml is missing: " + missing);

        assertTrue(List.of("total", "active").contains(config.getString("general.primary-metric")),
                "general.primary-metric picks what /playtime shows");
        assertTrue(List.of("total", "active", "primary").contains(config.getString("leaderboard.metric")));
        assertTrue(List.of("bossbar", "actionbar", "off").contains(config.getString("display.mode")));
        assertTrue(List.of("units", "words", "digital", "clock", "compact", "seconds")
                .contains(config.getString("format.style")));
        int perPage = config.getInt("leaderboard.per-page");
        assertTrue(perPage > 0 && perPage <= 45, "leaderboard.per-page must fit one GUI page, was " + perPage);
        assertTrue(config.getInt("display.update-seconds") >= 1);
        assertTrue(config.getInt("storage.autosave-minutes") >= 1);
        assertTrue(config.getInt("tracking.idle-seconds") >= 0);
        assertTrue(List.of("system", "lf", "crlf", "unix", "windows")
                .contains(config.getString("export.line-endings")));
        assertTrue(config.getInt("format.max-units") >= 0);
        assertTrue(config.getString("gui.progress-bar.filled").length() <= 2);
    }

    @Test
    @DisplayName("sounds.events matches the events the plugin plays")
    void soundEvents() throws Exception {
        YamlConfiguration config = resource("config.yml");
        ConfigurationSection events = config.getConfigurationSection("sounds.events");
        assertNotNull(events, "config.yml must list every sound event under sounds.events");
        assertEquals(SoundService.eventKeys(), events.getKeys(false),
                "every shipped sound key needs a config entry and vice versa");
        for (String key : SoundService.eventKeys()) {
            String sound = events.getString(key + ".sound");
            assertNotNull(sound, key + " needs a sound");
            assertNotNull(SoundService.defaultSound(key), key + " has no default sound");
            assertTrue(events.isInt(key + ".volume") || events.isDouble(key + ".volume"), key + ".volume");
            assertTrue(events.contains(key + ".pitch"), key + ".pitch");
            assertTrue(events.contains(key + ".category"), key + ".category");
            assertTrue(events.isBoolean(key + ".enabled"), key + ".enabled must be true or false");
        }
        assertTrue(config.getDouble("sounds.volume") >= 0.0 && config.getDouble("sounds.volume") <= 3.0);
        assertTrue(config.getDouble("sounds.pitch") > 0.0 && config.getDouble("sounds.pitch") <= 4.0);
        assertTrue(config.getBoolean("sounds.enabled"), "sounds ship enabled; the config switch turns them off");
    }

    @Test
    @DisplayName("milestones are configured the way the parser expects")
    void milestones() throws Exception {
        YamlConfiguration config = resource("config.yml");
        // The parser accepts a mapping, a list of mappings and plain scalars;
        // the shipped file uses the mapping form.
        ConfigurationSection list = config.getConfigurationSection("milestones.list");
        assertNotNull(list, "milestones.list must exist as a mapping of name -> settings");
        assertFalse(list.getKeys(false).isEmpty(), "the shipped config should demo at least one milestone");
        for (String name : list.getKeys(false)) {
            ConfigurationSection row = list.getConfigurationSection(name);
            assertNotNull(row, "milestone '" + name + "' needs its own block");
            boolean hasDuration = false;
            for (String key : List.of("seconds", "minutes", "hours", "days", "weeks", "duration", "time")) {
                if (row.contains(key)) {
                    hasDuration = true;
                    assertTrue(row.getString(key, "").matches("[0-9]+[a-z]*"),
                            name + "." + key + " should be a plain number or a short duration, got "
                                    + row.getString(key));
                }
            }
            assertTrue(hasDuration, name + " needs a duration (seconds/minutes/hours/days/weeks/duration)");
        }
        Set<String> names = new HashSet<>(list.getKeys(false));
        assertEquals(list.getKeys(false).size(), names.size(), "milestone names must be unique");
        assertTrue(config.getBoolean("milestones.enabled"), "milestones ship enabled");
    }

    // ------------------------------------------------------------- text hygiene

    @Test
    @DisplayName("no placeholder is left unclosed in any shipped text")
    void balancedPlaceholders() throws Exception {
        for (String name : List.of("messages.yml", "gui.yml", "config.yml")) {
            YamlConfiguration yaml = resource(name);
            checkPlaceholders(name, yaml);
        }
    }

    private static void checkPlaceholders(String where, ConfigurationSection section) {
        for (String key : section.getKeys(true)) {
            for (String value : section.getStringList(key)) {
                checkText(where + " -> " + key, value);
            }
            String value = section.isString(key) ? section.getString(key) : null;
            if (value != null) {
                checkText(where + " -> " + key, value);
            }
        }
    }

    private static void checkText(String where, String text) {
        for (String line : text.split("\n")) {
            int open = line.indexOf('{');
            while (open >= 0) {
                int close = line.indexOf('}', open);
                assertTrue(close > open, where + " has an unclosed placeholder: " + line);
                Matcher matcher = PLACEHOLDER.matcher(line);
                assertTrue(matcher.find(open) && matcher.group(1).length() > 0,
                        where + " has an empty placeholder name: " + line);
                open = line.indexOf('{', close);
            }
            assertTrue(line.indexOf('}') < 0 || line.indexOf('{') >= 0,
                    where + " has a stray closing brace: " + line);
        }
    }

    private static void assertMaterial(String where, String configured) {
        if (configured == null || configured.isBlank()) {
            return; // falls back to the plugin default, which is valid
        }
        Material material = Material.matchMaterial(configured.trim().toUpperCase(java.util.Locale.US));
        assertNotNull(material, where + " names an unknown material: " + configured);
        assertTrue(material.isItem(), where + " (" + configured + ") is not a usable item");
    }

    private static void assertAction(String where, String configured) {
        if (configured == null || configured.isBlank()) {
            return;
        }
        assertTrue(GuiConfig.ACTIONS.contains(configured.trim()),
                where + " uses an action nobody implements: " + configured + " (known: " + GuiConfig.ACTIONS + ")");
    }
}
