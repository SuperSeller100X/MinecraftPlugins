package dev.superseller.randomstructurechallenge.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.superseller.randomstructurechallenge.RandomStructureChallengePlugin;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed view of {@code config.yml}.
 */
public final class PluginSettings {

    private final RandomStructureChallengePlugin plugin;

    private int minInterval = 5;
    private int maxInterval = 3600;
    private int inputTimeout = 30;
    private boolean sameStructureForAll = true;
    private boolean includeSpectators = true;
    private int chunkRadius = 4;
    private int placementRetries = 8;
    private boolean safePocket = true;
    private List<String> worlds = List.of();
    private List<String> whitelist = List.of();
    private List<String> blacklist = List.of();

    private boolean bossbarEnabled = true;
    private String bossbarColor = "PURPLE";
    private String bossbarOverlay = "PROGRESS";
    private boolean actionbarEnabled = true;
    private int lowThreshold = 5;
    private int barWidth = 10;
    private boolean titleOnSpawn = true;

    private boolean soundsEnabled = true;
    private String soundStart = "minecraft:item.goat_horn.sound.0";
    private String soundSpawn = "minecraft:entity.wither.spawn";
    private String soundTickLow = "minecraft:block.note_block.hat";
    private String soundStop = "minecraft:ui.toast.challenge_complete";
    private String soundPause = "minecraft:block.note_block.bass";
    private String soundResume = "minecraft:block.note_block.pling";
    private float volume = 0.8f;
    private float pitch = 1.0f;

    public PluginSettings(RandomStructureChallengePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        minInterval = Math.max(1, cfg.getInt("settings.min-interval-seconds", 5));
        maxInterval = Math.max(minInterval, cfg.getInt("settings.max-interval-seconds", 3600));
        inputTimeout = Math.max(5, cfg.getInt("settings.input-timeout-seconds", 30));
        sameStructureForAll = cfg.getBoolean("settings.same-structure-for-all", true);
        includeSpectators = cfg.getBoolean("settings.include-spectators", true);
        chunkRadius = Math.max(0, Math.min(12, cfg.getInt("settings.load-chunk-radius", 4)));
        placementRetries = Math.max(1, Math.min(32, cfg.getInt("settings.placement-retries", 8)));
        safePocket = cfg.getBoolean("settings.safe-pocket", true);
        worlds = lowerCopy(cfg.getStringList("settings.worlds"));
        whitelist = keyCopy(cfg.getStringList("settings.structure-whitelist"));
        blacklist = keyCopy(cfg.getStringList("settings.structure-blacklist"));

        bossbarEnabled = cfg.getBoolean("display.bossbar.enabled", true);
        bossbarColor = cfg.getString("display.bossbar.color", "PURPLE");
        bossbarOverlay = cfg.getString("display.bossbar.overlay", "PROGRESS");
        actionbarEnabled = cfg.getBoolean("display.actionbar.enabled", true);
        lowThreshold = Math.max(1, cfg.getInt("display.actionbar.low-threshold", 5));
        barWidth = Math.max(4, Math.min(40, cfg.getInt("display.actionbar.bar-width", 10)));
        titleOnSpawn = cfg.getBoolean("display.title-on-spawn", true);

        soundsEnabled = cfg.getBoolean("sounds.enabled", true);
        soundStart = cfg.getString("sounds.start", soundStart);
        soundSpawn = cfg.getString("sounds.spawn", soundSpawn);
        soundTickLow = cfg.getString("sounds.tick-low", soundTickLow);
        soundStop = cfg.getString("sounds.stop", soundStop);
        soundPause = cfg.getString("sounds.pause", soundPause);
        soundResume = cfg.getString("sounds.resume", soundResume);
        volume = (float) cfg.getDouble("sounds.volume", 0.8);
        pitch = (float) cfg.getDouble("sounds.pitch", 1.0);
    }

    public int minInterval() {
        return minInterval;
    }

    public int maxInterval() {
        return maxInterval;
    }

    public int inputTimeout() {
        return inputTimeout;
    }

    public boolean sameStructureForAll() {
        return sameStructureForAll;
    }

    public boolean includeSpectators() {
        return includeSpectators;
    }

    public int chunkRadius() {
        return chunkRadius;
    }

    public int placementRetries() {
        return placementRetries;
    }

    public boolean safePocket() {
        return safePocket;
    }

    public List<String> worlds() {
        return worlds;
    }

    public List<String> whitelist() {
        return whitelist;
    }

    public List<String> blacklist() {
        return blacklist;
    }

    public boolean bossbarEnabled() {
        return bossbarEnabled;
    }

    public String bossbarColor() {
        return bossbarColor;
    }

    public String bossbarOverlay() {
        return bossbarOverlay;
    }

    public boolean actionbarEnabled() {
        return actionbarEnabled;
    }

    public int lowThreshold() {
        return lowThreshold;
    }

    public int barWidth() {
        return barWidth;
    }

    public boolean titleOnSpawn() {
        return titleOnSpawn;
    }

    public boolean soundsEnabled() {
        return soundsEnabled;
    }

    public String soundStart() {
        return soundStart;
    }

    public String soundSpawn() {
        return soundSpawn;
    }

    public String soundTickLow() {
        return soundTickLow;
    }

    public String soundStop() {
        return soundStop;
    }

    public String soundPause() {
        return soundPause;
    }

    public String soundResume() {
        return soundResume;
    }

    public float volume() {
        return volume;
    }

    public float pitch() {
        return pitch;
    }

    public boolean worldAllowed(String worldName) {
        if (worlds.isEmpty()) {
            return true;
        }
        if (worldName == null) {
            return false;
        }
        return worlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    private static List<String> lowerCopy(List<String> input) {
        List<String> out = new ArrayList<>();
        for (String value : input) {
            if (value != null && !value.isBlank()) {
                out.add(value.toLowerCase(Locale.ROOT).trim());
            }
        }
        return List.copyOf(out);
    }

    private static List<String> keyCopy(List<String> input) {
        List<String> out = new ArrayList<>();
        for (String value : input) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String key = value.toLowerCase(Locale.ROOT).trim();
            if (!key.contains(":")) {
                key = "minecraft:" + key;
            }
            out.add(key);
        }
        return List.copyOf(out);
    }
}
