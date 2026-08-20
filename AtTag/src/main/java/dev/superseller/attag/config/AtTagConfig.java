package dev.superseller.attag.config;

import dev.superseller.attag.util.MiniYaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Sound;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads config.yml from the plugin folder and copies the bundled default
 * on first run. AtTag is intentionally minimal: no commands, no
 * permissions — only the ping sounds are configurable.
 */
public final class AtTagConfig {

    private final JavaPlugin plugin;
    private final Logger logger;

    private Sound playerSound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
    private Sound everyoneSound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
    private float volume = 1.0f;
    private float pitch = 1.0f;

    public AtTagConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /** Reads the config file; falls back to defaults on any error. */
    public void load() {
        plugin.getDataFolder().mkdirs();
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            try (InputStream in = plugin.getResource("config.yml")) {
                if (in != null) {
                    Files.copy(in, file.toPath());
                    logger.info("Created default config.yml");
                }
            } catch (IOException e) {
                logger.warning("Could not create config.yml: " + e.getMessage());
            }
        }

        Map<String, Object> cfg = new LinkedHashMap<>();
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            cfg = MiniYaml.parse(text);
        } catch (IOException e) {
            logger.warning("Could not read config.yml: " + e.getMessage());
        }

        playerSound = parseSound(cfg.get("player-sound"), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, "player-sound");
        everyoneSound = parseSound(cfg.get("everyone-sound"), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, "everyone-sound");
        volume = clamp(parseNumber(cfg.get("volume"), 1.0f), 0.0f, 2.0f, "volume");
        pitch = clamp(parseNumber(cfg.get("pitch"), 1.0f), 0.5f, 2.0f, "pitch");

        logger.info("AtTag config: player-sound=" + playerSound
                + ", everyone-sound=" + everyoneSound
                + ", volume=" + volume + ", pitch=" + pitch);
    }

    private Sound parseSound(Object raw, Sound fallback, String key) {
        if (raw == null) {
            return fallback;
        }
        String name = raw.toString().trim().toUpperCase(Locale.ROOT);
        try {
            return Sound.valueOf(name);
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown sound '" + raw + "' for " + key + " — using " + fallback + ".");
            return fallback;
        }
    }

    private float parseNumber(Object raw, float fallback) {
        if (raw instanceof Number) {
            return ((Number) raw).floatValue();
        }
        if (raw != null) {
            try {
                return Float.parseFloat(raw.toString().trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return fallback;
    }

    private float clamp(float value, float min, float max, String key) {
        float result = Math.max(min, Math.min(max, value));
        if (result != value) {
            logger.warning(key + " out of range, clamped to " + result);
        }
        return result;
    }

    /** Sound played to a player mentioned with @playername. */
    public Sound playerSound() {
        return playerSound;
    }

    /** Sound played to everyone (except the sender) on @everyone/@all. */
    public Sound everyoneSound() {
        return everyoneSound;
    }

    public float volume() {
        return volume;
    }

    public float pitch() {
        return pitch;
    }
}
