package dev.superseller.swifttpa.config;

import dev.superseller.swifttpa.SwiftTPAPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed accessors over {@code config.yml}. All values are re-read from the
 * live {@link FileConfiguration} on every call, so {@code /stpaadmin reload}
 * picks up changes without any extra wiring.
 */
public final class SwiftTpaConfig {

    private final SwiftTPAPlugin plugin;

    public SwiftTpaConfig(SwiftTPAPlugin plugin) {
        this.plugin = plugin;
    }

    /** Reloads config.yml from disk. Kept as a named step for readability. */
    public void load() {
        plugin.reloadConfig();
        // Warn early about a bad storage type so a typo does not silently fall back.
        String type = storageType();
        if (!type.equals("yaml") && !type.equals("sqlite")) {
            plugin.getLogger().warning("Unknown storage.type '" + type + "' — falling back to yaml.");
        }
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    // ------------------------------------------------------------------ storage

    public String storageType() {
        return config().getString("storage.type", "yaml").toLowerCase(Locale.ROOT);
    }

    public boolean autosaveEnabled() {
        return config().getBoolean("storage.autosave.enabled", true);
    }

    public long autosaveIntervalSeconds() {
        return Math.max(30L, config().getLong("storage.autosave.interval-seconds", 300L));
    }

    // ------------------------------------------------------------------ requests

    /** Expiry in seconds; 0 means requests never expire. */
    public long requestExpireSeconds() {
        return Math.max(0L, config().getLong("requests.expire-seconds", 120L));
    }

    public int maxPendingPerTarget() {
        return Math.max(1, config().getInt("requests.max-pending-per-target", 5));
    }

    public boolean oneOutgoing() {
        return config().getBoolean("requests.one-outgoing", true);
    }

    // ------------------------------------------------------------------ cooldowns

    public long cooldownSeconds() {
        return Math.max(0L, config().getLong("cooldowns.request-seconds", 30L));
    }

    // ------------------------------------------------------------------ warmup

    public long warmupSeconds() {
        return Math.max(0L, config().getLong("warmup.seconds", 3L));
    }

    public boolean cancelOnMove() {
        return config().getBoolean("warmup.cancel-on-move", true);
    }

    public boolean cancelOnDamage() {
        return config().getBoolean("warmup.cancel-on-damage", true);
    }

    public boolean actionBarCountdown() {
        return config().getBoolean("warmup.action-bar", true);
    }

    // ------------------------------------------------------------------ teleport

    public boolean allowCrossWorld() {
        return config().getBoolean("teleport.allow-cross-world", true);
    }

    /** Lower-cased world names that may not be teleported into. */
    public Set<String> disabledWorlds() {
        Set<String> worlds = new HashSet<>();
        for (String world : config().getStringList("teleport.disabled-worlds")) {
            if (world != null && !world.isBlank()) {
                worlds.add(world.toLowerCase(Locale.ROOT));
            }
        }
        return worlds;
    }

    public boolean isWorldDisabled(String worldName) {
        return worldName != null && disabledWorlds().contains(worldName.toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ features

    public boolean spyEnabled() {
        return config().getBoolean("features.spy", true);
    }

    // ------------------------------------------------------------------ sounds

    public boolean soundsEnabled() {
        return config().getBoolean("sounds.enabled", true);
    }

    /**
     * Builds the Adventure sound for an event key, or null when the event is
     * muted or misconfigured. Accepts vanilla keys with or without the
     * {@code minecraft:} namespace.
     */
    public Sound sound(String key, Float pitchOverride) {
        String raw = config().getString("sounds." + key + ".sound", "");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9_./:]+")) {
            plugin.getLogger().warning("Invalid sound key for sounds." + key + ": '" + raw + "'");
            return null;
        }
        try {
            Key soundKey = value.contains(":")
                    ? Key.key(value)
                    : Key.key(Key.MINECRAFT_NAMESPACE, value);
            float volume = (float) config().getDouble("sounds." + key + ".volume", 1.0);
            float pitch = pitchOverride != null
                    ? pitchOverride
                    : (float) config().getDouble("sounds." + key + ".pitch", 1.0);
            return Sound.sound(soundKey, Sound.Source.MASTER, volume, pitch);
        } catch (Throwable e) {
            plugin.getLogger().warning("Invalid sound key for sounds." + key + ": '" + raw + "'");
            return null;
        }
    }

    // ------------------------------------------------------------------ gui

    public String guiTitle() {
        return config().getString("gui.title", "<dark_aqua><bold>Teleport Requests</bold></dark_aqua>");
    }

    /** Parses a gui.icons.* material, falling back when absent or invalid. */
    public Material guiIcon(String name, Material fallback) {
        String raw = config().getString("gui.icons." + name, "");
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            Material material = Material.matchMaterial(raw.trim());
            return material != null ? material : fallback;
        } catch (Throwable e) {
            return fallback;
        }
    }

    /** Reserved helper for future list-style options. */
    public List<String> rawList(String path) {
        return config().getStringList(path);
    }
}
