package dev.superseller.gifty.config;

import dev.superseller.gifty.util.Colors;
import dev.superseller.gifty.util.MiniYaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads config.yml and messages.yml from the plugin folder, copying the
 * defaults from the jar on first run. Every message supports '&' and hex
 * ('&#RRGGBB') color codes and {placeholders}.
 */
public final class GiftyConfig {

    private final JavaPlugin plugin;
    private final Logger logger;

    private Map<String, Object> config = new LinkedHashMap<>();
    private Map<String, Object> messages = new LinkedHashMap<>();

    public GiftyConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        config = loadOrCopy("config.yml");
        messages = loadOrCopy("messages.yml");
    }

    private Map<String, Object> loadOrCopy(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            try (InputStream in = plugin.getResource(name)) {
                if (in != null) {
                    Files.copy(in, file.toPath());
                    logger.info("Created default " + name);
                }
            } catch (IOException e) {
                logger.warning("Could not create " + name + ": " + e.getMessage());
            }
        }
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return MiniYaml.parse(text);
        } catch (IOException e) {
            logger.warning("Could not read " + name + ": " + e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    // ------------------------------------------------------------ general

    @SuppressWarnings("unchecked")
    private Map<String, Object> section(String key) {
        Object o = config.get(key);
        if (o instanceof Map) {
            return (Map<String, Object>) o;
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> general() {
        return section("general");
    }

    public int sendSlots() {
        return clampInt(general().get("send-slots"), 1, 1, 54);
    }

    public long cooldownSeconds() {
        return clampLong(general().get("cooldown-seconds"), 60, 0, Integer.MAX_VALUE);
    }

    public int maxPendingPerPlayer() {
        return clampInt(general().get("max-pending-per-player"), 108, 1, 100000);
    }

    public int inboxPages() {
        return clampInt(general().get("inbox-pages"), 2, 1, 100);
    }

    public long expireHours() {
        return clampLong(general().get("expire-hours"), 0, 0, 8760);
    }

    public boolean notifyOnSend() {
        return bool(general().get("notify-on-send"), true);
    }

    public boolean playSounds() {
        return bool(general().get("play-sounds"), true);
    }

    public int maxMessageLength() {
        return clampInt(general().get("max-message-length"), 100, 1, 1000);
    }

    public long saveIntervalSeconds() {
        return clampLong(general().get("save-interval-seconds"), 300, 10, 86400);
    }

    public String economyMode() {
        Object o = section("economy").get("enabled");
        return o == null ? "auto" : String.valueOf(o);
    }

    /** Uppercase material names that may not be gifted. */
    public java.util.List<String> blacklistedMaterials() {
        Object o = general().get("blacklisted-materials");
        java.util.List<String> out = new java.util.ArrayList<>();
        if (o instanceof java.util.List) {
            for (Object item : (java.util.List<?>) o) {
                out.add(String.valueOf(item).toUpperCase());
            }
        }
        return out;
    }

    // ------------------------------------------------------------ messages

    public String msg(String path, Object... keyValues) {
        Object o = lookup(path);
        if (o == null) {
            return "&cMissing message: " + path;
        }
        String s = String.valueOf(o);
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            s = s.replace("{" + keyValues[i] + "}", String.valueOf(keyValues[i + 1]));
        }
        return s;
    }

    private Object lookup(String path) {
        Object current = messages;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(part);
        }
        return current;
    }

    public String prefix() {
        return msg("prefix");
    }

    /**
     * Formats a message: replaces {prefix} and any {key} placeholders, then
     * translates color codes. keyValues is alternating key/value pairs.
     */
    public String format(String path, Object... keyValues) {
        String s = msg(path);
        s = s.replace("{prefix}", prefix());
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            s = s.replace("{" + keyValues[i] + "}", String.valueOf(keyValues[i + 1]));
        }
        return Colors.parse(s);
    }

    // ------------------------------------------------------------ helpers

    private static int clampInt(Object o, int def, int min, int max) {
        int v = def;
        if (o instanceof Number) {
            v = ((Number) o).intValue();
        } else if (o != null) {
            try {
                v = Integer.parseInt(String.valueOf(o));
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(min, Math.min(max, v));
    }

    private static long clampLong(Object o, long def, long min, long max) {
        long v = def;
        if (o instanceof Number) {
            v = ((Number) o).longValue();
        } else if (o != null) {
            try {
                v = Long.parseLong(String.valueOf(o));
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(min, Math.min(max, v));
    }

    private static boolean bool(Object o, boolean def) {
        if (o instanceof Boolean) {
            return (Boolean) o;
        }
        if (o != null) {
            return Boolean.parseBoolean(String.valueOf(o));
        }
        return def;
    }
}
