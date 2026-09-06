package dev.superseller.hourglass.config;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.superseller.hourglass.data.Milestone;
import dev.superseller.hourglass.util.CsvBuilder;
import dev.superseller.hourglass.util.TimeFormat;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Typed, immutable view over {@code config.yml}.
 *
 * <p>All YAML reading happens in this class so that every option has exactly
 * one documented default and the shipped {@code config.yml} can be verified
 * against the code (see {@code tools/check_consistency.py}). Reload
 * ({@code /playtimeadmin reload}) rebuilds the whole object.
 */
public final class HourGlassConfig {

    /** Supported values of {@code general.primary-metric}. */
    public enum PrimaryMetric {
        TOTAL, ACTIVE;

        public static PrimaryMetric parse(String value) {
            return value != null && value.trim().equalsIgnoreCase("active") ? ACTIVE : TOTAL;
        }
    }

    private final JavaPlugin plugin;

    // general
    private PrimaryMetric primaryMetric = PrimaryMetric.TOTAL;
    private ZoneId zone = ZoneId.systemDefault();
    private String dateFormat = "yyyy-MM-dd HH:mm";
    private boolean relativeTimes = true;

    // storage
    private int autosaveMinutes = 5;
    private boolean saveOnQuit = true;
    private boolean flushOnDisable = true;
    private int historySize = 10;
    private int maxPlayers = 0;

    // tracking
    private boolean idleDetection = true;
    private long idleMillis = 300_000L;
    private boolean adminEditsCountAsActive = true;
    private long maxTickGapMillis = 300_000L;
    private final Set<String> ignoreWorlds = new HashSet<>();

    // leaderboard
    private boolean leaderboardEnabled = true;
    private String leaderboardMetric = "total";
    private int leaderboardPerPage = 10;
    private int leaderboardRefreshSeconds = 60;
    private boolean leaderboardIncludeOffline = true;
    private int leaderboardLimit = 500;
    private boolean leaderboardSelfHighlight = true;

    // display
    private String displayMode = "none";
    private String displayMetric = "primary";
    private int displayUpdateSeconds = 1;
    private String bossBarColor = "PURPLE";
    private String bossBarOverlay = "NOTCHED_6";
    private String bossBarProgress = "day";

    // join / quit
    private boolean joinSummary = true;
    private int joinSummaryDelaySeconds = 3;
    private boolean joinSummarySound = true;
    private boolean quitStaffBroadcast = false;

    // milestones
    private boolean milestonesEnabled = true;
    private String milestoneMetric = "total";
    private int milestoneCheckSeconds = 30;
    private boolean milestoneBroadcast = true;
    private boolean milestoneTitle = true;
    private List<Milestone> milestones = List.of();

    // gui
    private boolean guiFill = true;
    private boolean guiCloseOnMove = false;
    private boolean guiLeaderboardHeads = true;
    private boolean guiAllowViewOthers = true;
    private String guiDefaultPage = "stats";
    private String progressBarFilled = "\u2588";
    private String progressBarEmpty = "\u2591";
    private int progressBarLength = 12;

    // export
    private String exportDirectory = "exports";
    private CsvBuilder.Newline exportNewline = CsvBuilder.Newline.SYSTEM;
    private boolean exportBom = true;
    private boolean exportIncludeOffline = true;
    private String exportSort = "total";

    // purge
    private int purgeDefaultDays = 180;

    // admin
    private boolean resetKeepsFirstJoin = true;

    // placeholders
    private boolean placeholdersEnabled = true;
    private List<String> placeholderAliases = List.of("hourglass", "playtime", "hg");

    // commands
    private boolean commandsAllowConsole = true;
    private int commandsTabLimit = 50;
    private boolean commandsShortAliases = true;

    // sound
    private boolean soundsEnabled = true;
    private float soundsVolume = 0.7f;
    private float soundsPitch = 1.0f;

    // format
    private TimeFormat.Settings formatSettings = TimeFormat.Settings.defaults();
    private TimeFormat timeFormat = new TimeFormat(formatSettings);

    private boolean debug = false;
    private int configVersion = 0;

    public HourGlassConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** (Re)reads every option from the given YAML file. */
    public void load(YamlConfiguration yaml) {
        configVersion = yaml.getInt("config-version", 0);

        primaryMetric = PrimaryMetric.parse(yaml.getString("general.primary-metric", "total"));
        zone = readZone(yaml.getString("general.time-zone", "system"));
        String configuredPattern = yaml.getString("general.date-format", "auto");
        boolean clock24 = yaml.getBoolean("general.clock-24h", true);
        dateFormat = configuredPattern == null || configuredPattern.isBlank()
                || configuredPattern.equalsIgnoreCase("auto")
                ? (clock24 ? "yyyy-MM-dd HH:mm" : "yyyy-MM-dd hh:mm a")
                : configuredPattern;
        relativeTimes = yaml.getBoolean("general.relative-times", true);

        autosaveMinutes = Math.max(0, yaml.getInt("storage.autosave-minutes", 5));
        saveOnQuit = yaml.getBoolean("storage.save-on-quit", true);
        flushOnDisable = yaml.getBoolean("storage.flush-on-disable", true);
        historySize = Math.max(0, yaml.getInt("storage.history-size", 10));
        maxPlayers = Math.max(0, yaml.getInt("storage.max-players", 0));

        idleDetection = yaml.getBoolean("tracking.idle-detection", true);
        idleMillis = Math.max(0L, yaml.getLong("tracking.idle-seconds", 300L) * 1_000L);
        adminEditsCountAsActive = yaml.getBoolean("tracking.admin-edits-count-as-active", true);
        maxTickGapMillis = Math.max(1L, yaml.getLong("tracking.max-tick-gap-seconds", 300L) * 1_000L);
        ignoreWorlds.clear();
        for (String world : yaml.getStringList("tracking.ignore-worlds")) {
            if (world != null && !world.isBlank()) {
                ignoreWorlds.add(world.trim().toLowerCase(Locale.US));
            }
        }

        leaderboardEnabled = yaml.getBoolean("leaderboard.enabled", true);
        leaderboardMetric = norm(yaml.getString("leaderboard.metric", "total"), "total", "active", "primary");
        leaderboardPerPage = clamp(yaml.getInt("leaderboard.per-page", 10), 1, 45);
        leaderboardRefreshSeconds = Math.max(5, yaml.getInt("leaderboard.refresh-seconds", 60));
        leaderboardIncludeOffline = yaml.getBoolean("leaderboard.include-offline", true);
        leaderboardLimit = Math.max(0, yaml.getInt("leaderboard.limit", 500));
        leaderboardSelfHighlight = yaml.getBoolean("leaderboard.self-highlight", true);

        displayMode = norm(yaml.getString("display.mode", "none"), "none", "bossbar", "actionbar", "both");
        displayMetric = norm(yaml.getString("display.metric", "primary"), "primary", "total", "active",
                "session", "session-active");
        displayUpdateSeconds = Math.max(1, yaml.getInt("display.update-seconds", 1));
        bossBarColor = yaml.getString("display.bossbar.color", "PURPLE");
        bossBarOverlay = yaml.getString("display.bossbar.overlay", "NOTCHED_6");
        bossBarProgress = norm(yaml.getString("display.bossbar.progress", "day"), "day", "week", "milestone", "none");

        joinSummary = yaml.getBoolean("join.summary-enabled", true);
        joinSummaryDelaySeconds = Math.max(0, yaml.getInt("join.summary-delay-seconds", 3));
        joinSummarySound = yaml.getBoolean("join.sound", true);
        quitStaffBroadcast = yaml.getBoolean("quit.staff-broadcast", false);

        milestonesEnabled = yaml.getBoolean("milestones.enabled", true);
        milestoneMetric = norm(yaml.getString("milestones.metric", "total"), "total", "active", "primary");
        milestoneCheckSeconds = Math.max(1, yaml.getInt("milestones.check-seconds", 30));
        milestoneBroadcast = yaml.getBoolean("milestones.broadcast", true);
        milestoneTitle = yaml.getBoolean("milestones.show-title", true);
        milestones = readMilestones(yaml);

        guiFill = yaml.getBoolean("gui.fill", true);
        guiCloseOnMove = yaml.getBoolean("gui.close-on-move", false);
        guiLeaderboardHeads = yaml.getBoolean("gui.leaderboard-heads", true);
        guiAllowViewOthers = yaml.getBoolean("gui.allow-view-others", true);
        guiDefaultPage = norm(yaml.getString("gui.default-gui", "stats"), "stats", "leaderboard", "milestones");
        progressBarFilled = yaml.getString("gui.progress-bar.filled", "\u2588");
        progressBarEmpty = yaml.getString("gui.progress-bar.empty", "\u2591");
        progressBarLength = clamp(yaml.getInt("gui.progress-bar.length", 12), 1, 40);

        exportDirectory = yaml.getString("export.directory", "exports");
        exportNewline = CsvBuilder.Newline.parse(yaml.getString("export.line-endings", "system"));
        exportBom = yaml.getBoolean("export.add-bom", true);
        exportIncludeOffline = yaml.getBoolean("export.include-offline", true);
        exportSort = norm(yaml.getString("export.sort", "total"), "total", "active", "name", "last-seen");

        purgeDefaultDays = Math.max(1, yaml.getInt("purge.default-days", 180));
        resetKeepsFirstJoin = yaml.getBoolean("admin.reset-keeps-first-join", true);

        placeholdersEnabled = yaml.getBoolean("placeholders.enabled", true);
        List<String> aliases = yaml.getStringList("placeholders.aliases");
        if (!aliases.isEmpty()) {
            List<String> cleaned = new ArrayList<>(aliases.size());
            for (String alias : aliases) {
                if (alias != null && !alias.isBlank()) {
                    cleaned.add(alias.trim().toLowerCase(Locale.US));
                }
            }
            if (!cleaned.isEmpty()) {
                placeholderAliases = List.copyOf(cleaned);
            }
        }

        commandsAllowConsole = yaml.getBoolean("commands.allow-console", true);
        commandsTabLimit = clamp(yaml.getInt("commands.tab-limit", 50), 1, 1000);
        commandsShortAliases = yaml.getBoolean("commands.short-aliases", true);

        soundsEnabled = yaml.getBoolean("sounds.enabled", true);
        soundsVolume = (float) clamp(yaml.getDouble("sounds.volume", 0.7d), 0.0d, 10.0d);
        soundsPitch = (float) clamp(yaml.getDouble("sounds.pitch", 1.0d), 0.01d, 10.0d);

        formatSettings = readFormat(yaml);
        timeFormat = new TimeFormat(formatSettings);

        debug = yaml.getBoolean("debug", false);
    }

    private static ZoneId readZone(String configured) {
        return dev.superseller.hourglass.util.Dates.resolveZone(configured);
    }

    private static String norm(String value, String... allowed) {
        if (value == null || value.isBlank()) {
            return allowed[0];
        }
        String candidate = value.trim().toLowerCase(Locale.US);
        for (String option : allowed) {
            if (option.equals(candidate)) {
                return candidate;
            }
        }
        return allowed[0];
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }

    private List<Milestone> readMilestones(YamlConfiguration yaml) {
        List<Milestone> out = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();
        Object node = yaml.get("milestones.list");
        int index = 0;
        if (node instanceof ConfigurationSection section) {
            for (String key : section.getKeys(false)) {
                Milestone milestone = fromSection(key, section.getConfigurationSection(key), index, usedNames);
                if (milestone != null) {
                    out.add(milestone);
                    index++;
                }
            }
        } else if (node instanceof List<?> list) {
            for (Object element : list) {
                Milestone milestone = fromListEntry(element, index, usedNames);
                if (milestone != null) {
                    out.add(milestone);
                    index++;
                }
            }
        }
        out.sort((a, b) -> Long.compare(a.seconds(), b.seconds()));
        return List.copyOf(out);
    }

    /** {@code milestones.list:} written as a map of named entries. */
    private Milestone fromSection(String key, ConfigurationSection section, int index, Set<String> usedNames) {
        if (section == null) {
            // "night-shift: 10" — the value alone is the threshold in hours.
            return fromScalar(key, null, index, usedNames);
        }
        long seconds = secondsOf(section);
        if (seconds <= 0L) {
            warnMilestone(key);
            return null;
        }
        String name = unique(section.getString("name", key), seconds, index, usedNames);
        return new Milestone(name, seconds, section.getString("message", null),
                List.copyOf(section.getStringList("commands")), section.getString("sound", null),
                section.getBoolean("broadcast", milestoneBroadcast), section.getBoolean("title", milestoneTitle));
    }

    /** {@code milestones.list:} written as a sequence of hours or maps. */
    private Milestone fromListEntry(Object element, int index, Set<String> usedNames) {
        if (element instanceof ConfigurationSection section) {
            return fromSection(section.getName(), section, index, usedNames);
        }
        if (element instanceof java.util.Map<?, ?> map) {
            org.bukkit.configuration.MemoryConfiguration wrapped =
                    new org.bukkit.configuration.MemoryConfiguration();
            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    wrapped.set(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            String name = String.valueOf(map.getOrDefault("name", "milestone-" + index));
            long seconds = secondsOf(wrapped);
            if (seconds <= 0L) {
                warnMilestone(name);
                return null;
            }
            return new Milestone(unique(name, seconds, index, usedNames), seconds,
                    wrapped.getString("message", null),
                    List.copyOf(wrapped.getStringList("commands")), wrapped.getString("sound", null),
                    wrapped.getBoolean("broadcast", milestoneBroadcast),
                    wrapped.getBoolean("title", milestoneTitle));
        }
        return fromScalar(null, element, index, usedNames);
    }

    /** A bare {@code 1}, {@code "10h"} or {@code 0.5} entry. */
    private Milestone fromScalar(String key, Object value, int index, Set<String> usedNames) {
        long seconds;
        String label;
        if (value instanceof Number number) {
            seconds = (long) Math.floor(number.doubleValue() * TimeFormat.HOUR);
            label = key != null ? key : number + "h";
        } else if (value != null) {
            String text = String.valueOf(value).trim();
            var parsed = TimeFormat.parse(text);
            if (parsed.isPresent()) {
                seconds = parsed.getAsLong();
            } else if (text.matches("\\d+(\\.\\d+)?")) {
                seconds = (long) Math.floor(Double.parseDouble(text) * TimeFormat.HOUR);
            } else {
                return null;
            }
            label = key != null ? key : text;
        } else {
            return null;
        }
        if (seconds <= 0L) {
            warnMilestone(label);
            return null;
        }
        return new Milestone(unique(label, seconds, index, usedNames), seconds, null, List.of(), null,
                milestoneBroadcast, milestoneTitle);
    }

    private static long secondsOf(ConfigurationSection section) {
        if (section.contains("seconds")) {
            return Math.max(0L, section.getLong("seconds", 0L));
        }
        if (section.contains("minutes")) {
            return (long) Math.floor(section.getDouble("minutes", 0d) * TimeFormat.MINUTE);
        }
        if (section.contains("days")) {
            return (long) Math.floor(section.getDouble("days", 0d) * TimeFormat.DAY);
        }
        if (section.contains("weeks")) {
            return (long) Math.floor(section.getDouble("weeks", 0d) * TimeFormat.WEEK);
        }
        String hours = section.getString("hours", null);
        if (hours != null) {
            var parsed = TimeFormat.parse(hours);
            if (parsed.isPresent() && !hours.matches("\\d+(\\.\\d+)?")) {
                return parsed.getAsLong();
            }
            try {
                return (long) Math.floor(Double.parseDouble(hours) * TimeFormat.HOUR);
            } catch (NumberFormatException e) {
                return -1L;
            }
        }
        String duration = section.getString("duration", null);
        if (duration != null) {
            return TimeFormat.parse(duration).orElse(-1L);
        }
        return -1L;
    }

    private String unique(String rawName, long seconds, int index, Set<String> usedNames) {
        String name = Milestone.normalizeName(rawName, seconds);
        if (!usedNames.add(name)) {
            name = name + "-" + index;
            usedNames.add(name);
        }
        return name;
    }

    private void warnMilestone(String label) {
        plugin.getLogger().warning("Milestone '" + label + "' has no usable duration; skipped."
                + " Add 'hours:', 'minutes:', 'days:' or 'seconds:'.");
    }

    private TimeFormat.Settings readFormat(YamlConfiguration yaml) {
        TimeFormat.Style style = switch (norm(yaml.getString("format.style", "units"),
                "units", "words", "digital", "compact", "clock", "seconds")) {
            case "words" -> TimeFormat.Style.WORDS;
            case "digital" -> TimeFormat.Style.DIGITAL;
            case "compact" -> TimeFormat.Style.COMPACT;
            case "clock" -> TimeFormat.Style.CLOCK;
            case "seconds" -> TimeFormat.Style.SECONDS;
            default -> TimeFormat.Style.UNITS;
        };
        return new TimeFormat.Settings(
                style,
                clamp(yaml.getInt("format.max-units", 3), 0, 7),
                yaml.getBoolean("format.include-seconds", true),
                yaml.getString("format.separator", " "),
                yaml.getString("format.zero-text", "a brand new player"),
                yaml.getString("format.units.year", "y,year,years"),
                yaml.getString("format.units.month", "mo,month,months"),
                yaml.getString("format.units.week", "w,week,weeks"),
                yaml.getString("format.units.day", "d,day,days"),
                yaml.getString("format.units.hour", "h,hour,hours"),
                yaml.getString("format.units.minute", "m,minute,minutes"),
                yaml.getString("format.units.second", "s,second,seconds"),
                norm(yaml.getString("format.parse.default-unit", "seconds"), "seconds", "minutes"));
    }

    // ------------------------------------------------------------------ getters

    public JavaPlugin plugin() {
        return plugin;
    }

    public int configVersion() {
        return configVersion;
    }

    public PrimaryMetric primaryMetric() {
        return primaryMetric;
    }

    public boolean primaryIsActive() {
        return primaryMetric == PrimaryMetric.ACTIVE;
    }

    public ZoneId zone() {
        return zone;
    }

    public String dateFormat() {
        return dateFormat;
    }

    public boolean relativeTimes() {
        return relativeTimes;
    }

    public int autosaveMinutes() {
        return autosaveMinutes;
    }

    public boolean saveOnQuit() {
        return saveOnQuit;
    }

    public boolean flushOnDisable() {
        return flushOnDisable;
    }

    public int historySize() {
        return historySize;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public boolean idleDetection() {
        return idleDetection;
    }

    public long idleMillis() {
        return idleMillis;
    }

    public boolean adminEditsCountAsActive() {
        return adminEditsCountAsActive;
    }

    /**
     * Upper bound for one accumulation interval. A server that was suspended
     * (VM pause, SIGSTOP, laptop sleep) must not hand out hours of free
     * playtime, so any larger gap is clamped to this.
     */
    public long maxTickGapMillis() {
        return maxTickGapMillis;
    }

    public Set<String> ignoreWorlds() {
        return Set.copyOf(ignoreWorlds);
    }

    public boolean worldIgnored(String name) {
        return name != null && ignoreWorlds.contains(name.toLowerCase(Locale.US));
    }

    public boolean leaderboardEnabled() {
        return leaderboardEnabled;
    }

    public String leaderboardMetric() {
        return leaderboardMetric;
    }

    public int leaderboardPerPage() {
        return leaderboardPerPage;
    }

    public int leaderboardRefreshSeconds() {
        return leaderboardRefreshSeconds;
    }

    public boolean leaderboardIncludeOffline() {
        return leaderboardIncludeOffline;
    }

    public int leaderboardLimit() {
        return leaderboardLimit;
    }

    public boolean leaderboardSelfHighlight() {
        return leaderboardSelfHighlight;
    }

    public String displayMode() {
        return displayMode;
    }

    public String displayMetric() {
        return displayMetric;
    }

    public int displayUpdateSeconds() {
        return displayUpdateSeconds;
    }

    public String bossBarColor() {
        return bossBarColor;
    }

    public String bossBarOverlay() {
        return bossBarOverlay;
    }

    public String bossBarProgress() {
        return bossBarProgress;
    }

    public boolean joinSummary() {
        return joinSummary;
    }

    public int joinSummaryDelaySeconds() {
        return joinSummaryDelaySeconds;
    }

    public boolean joinSummarySound() {
        return joinSummarySound;
    }

    public boolean quitStaffBroadcast() {
        return quitStaffBroadcast;
    }

    public boolean milestonesEnabled() {
        return milestonesEnabled;
    }

    public String milestoneMetric() {
        return milestoneMetric;
    }

    public int milestoneCheckSeconds() {
        return milestoneCheckSeconds;
    }

    public boolean milestoneBroadcast() {
        return milestoneBroadcast;
    }

    public boolean milestoneTitle() {
        return milestoneTitle;
    }

    public List<Milestone> milestones() {
        return milestones;
    }

    public Milestone milestoneAt(int index) {
        return index >= 0 && index < milestones.size() ? milestones.get(index) : null;
    }

    public boolean guiFill() {
        return guiFill;
    }

    public boolean guiCloseOnMove() {
        return guiCloseOnMove;
    }

    public boolean guiLeaderboardHeads() {
        return guiLeaderboardHeads;
    }

    public boolean guiAllowViewOthers() {
        return guiAllowViewOthers;
    }

    public String guiDefaultPage() {
        return guiDefaultPage;
    }

    /** Characters used by the milestone progress bar in the GUI. */
    public String progressBarFilled() {
        return progressBarFilled == null || progressBarFilled.isEmpty() ? "\u2588" : progressBarFilled;
    }

    public String progressBarEmpty() {
        return progressBarEmpty == null || progressBarEmpty.isEmpty() ? "\u2591" : progressBarEmpty;
    }

    public int progressBarLength() {
        return progressBarLength;
    }

    public String exportDirectory() {
        return exportDirectory;
    }

    public CsvBuilder.Newline exportNewline() {
        return exportNewline;
    }

    public boolean exportBom() {
        return exportBom;
    }

    public boolean exportIncludeOffline() {
        return exportIncludeOffline;
    }

    public String exportSort() {
        return exportSort;
    }

    public int purgeDefaultDays() {
        return purgeDefaultDays;
    }

    /** Whether {@code /playtimeadmin reset} keeps the first-join stamp. */
    public boolean resetKeepsFirstJoin() {
        return resetKeepsFirstJoin;
    }

    public boolean placeholdersEnabled() {
        return placeholdersEnabled;
    }

    public List<String> placeholderAliases() {
        return placeholderAliases;
    }

    public boolean commandsAllowConsole() {
        return commandsAllowConsole;
    }

    public int commandsTabLimit() {
        return commandsTabLimit;
    }

    public boolean commandsShortAliases() {
        return commandsShortAliases;
    }

    public boolean soundsEnabled() {
        return soundsEnabled;
    }

    public float soundsVolume() {
        return soundsVolume;
    }

    public float soundsPitch() {
        return soundsPitch;
    }

    public TimeFormat timeFormat() {
        return timeFormat;
    }

    public TimeFormat.Settings formatSettings() {
        return formatSettings;
    }

    public boolean debug() {
        return debug;
    }

    /** The metric name that {@code primary} resolves to for a snapshot. */
    public String resolveMetric(String requested) {
        if (requested == null) {
            return primaryIsActive() ? "active" : "total";
        }
        return switch (requested) {
            case "primary" -> primaryIsActive() ? "active" : "total";
            case "session" -> "session";
            case "session-active" -> "session-active";
            case "active" -> "active";
            default -> "total";
        };
    }
}
