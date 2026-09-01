package dev.superseller.easymending.config;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Manages plugin localization, MiniMessage tags, and legacy color code formatting.
 * Guaranteed to never throw uncaught exceptions during message parsing.
 */
public final class Messages {

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacySection();
    private FileConfiguration config;
    private String prefix = "<gradient:#4facfe:#00f2fe><b>EasyMending</b></gradient> <dark_gray>»</dark_gray> ";

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads or reloads the messages.yml configuration, applying built-in defaults.
     */
    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);

        // Apply fallback defaults from embedded resource
        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream != null) {
            try (InputStreamReader reader = new InputStreamReader(defStream, StandardCharsets.UTF_8)) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(reader);
                this.config.setDefaults(defConfig);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Could not load default messages.yml resource", e);
            }
        }

        this.prefix = config.getString("prefix", prefix);
    }

    /**
     * Resolves a raw string template with supplied placeholder replacements.
     * Supports MiniMessage syntax, legacy ampersand (&) codes, and section (§) codes.
     *
     * @param key message key
     * @param placeholders key-value pairs
     * @return formatted Component
     */
    public Component get(String key, Map<String, String> placeholders) {
        String raw = config != null ? config.getString(key, "<red>Missing message: " + key + "</red>") : key;
        if (raw == null) {
            raw = "<red>Missing message: " + key + "</red>";
        }

        raw = raw.replace("<prefix>", prefix);

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String k = entry.getKey();
                String v = entry.getValue() != null ? entry.getValue() : "";
                raw = raw.replace("<" + k + ">", v);
                raw = raw.replace("{" + k + "}", v);
            }
        }

        // Convert legacy color codes to MiniMessage tags for seamless gradient and tag compatibility
        raw = convertLegacyToMiniMessage(raw);

        try {
            return miniMessage.deserialize(raw);
        } catch (Throwable e) {
            try {
                return legacySerializer.deserialize(raw);
            } catch (Throwable fallbackError) {
                return Component.text(raw);
            }
        }
    }

    /**
     * Translates legacy color and format codes (& and §) into standard MiniMessage tags.
     */
    public static String convertLegacyToMiniMessage(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Handle case-insensitive & and § codes
        return text
                .replace("&0", "<black>").replace("§0", "<black>")
                .replace("&1", "<dark_blue>").replace("§1", "<dark_blue>")
                .replace("&2", "<dark_green>").replace("§2", "<dark_green>")
                .replace("&3", "<dark_aqua>").replace("§3", "<dark_aqua>")
                .replace("&4", "<dark_red>").replace("§4", "<dark_red>")
                .replace("&5", "<dark_purple>").replace("§5", "<dark_purple>")
                .replace("&6", "<gold>").replace("§6", "<gold>")
                .replace("&7", "<gray>").replace("§7", "<gray>")
                .replace("&8", "<dark_gray>").replace("§8", "<dark_gray>")
                .replace("&9", "<blue>").replace("§9", "<blue>")
                .replace("&a", "<green>").replace("&A", "<green>").replace("§a", "<green>").replace("§A", "<green>")
                .replace("&b", "<aqua>").replace("&B", "<aqua>").replace("§b", "<aqua>").replace("§B", "<aqua>")
                .replace("&c", "<red>").replace("&C", "<red>").replace("§c", "<red>").replace("§C", "<red>")
                .replace("&d", "<light_purple>").replace("&D", "<light_purple>").replace("§d", "<light_purple>").replace("§D", "<light_purple>")
                .replace("&e", "<yellow>").replace("&E", "<yellow>").replace("§e", "<yellow>").replace("§E", "<yellow>")
                .replace("&f", "<white>").replace("&F", "<white>").replace("§f", "<white>").replace("§F", "<white>")
                .replace("&k", "<obfuscated>").replace("&K", "<obfuscated>").replace("§k", "<obfuscated>").replace("§K", "<obfuscated>")
                .replace("&l", "<bold>").replace("&L", "<bold>").replace("§l", "<bold>").replace("§L", "<bold>")
                .replace("&m", "<strikethrough>").replace("&M", "<strikethrough>").replace("§m", "<strikethrough>").replace("§M", "<strikethrough>")
                .replace("&n", "<underlined>").replace("&N", "<underlined>").replace("§n", "<underlined>").replace("§N", "<underlined>")
                .replace("&o", "<italic>").replace("&O", "<italic>").replace("§o", "<italic>").replace("§O", "<italic>")
                .replace("&r", "<reset>").replace("&R", "<reset>").replace("§r", "<reset>").replace("§R", "<reset>");
    }

    /**
     * Backward-compatible alias for convertLegacyToMiniMessage.
     */
    public static String convertAmpersandToMiniMessage(String text) {
        return convertLegacyToMiniMessage(text);
    }

    /**
     * Sends a localized message to a CommandSender with optional placeholder replacements.
     * Guaranteed to never throw uncaught exceptions to the caller.
     *
     * @param sender target recipient
     * @param key message key
     * @param placeholderPairs alternating key and value strings
     */
    public void send(CommandSender sender, String key, String... placeholderPairs) {
        if (sender == null) return;
        try {
            Map<String, String> placeholders = new HashMap<>();
            if (placeholderPairs != null) {
                for (int i = 0; i < placeholderPairs.length - 1; i += 2) {
                    String k = placeholderPairs[i];
                    String v = placeholderPairs[i + 1];
                    if (k != null) {
                        placeholders.put(k, v != null ? v : "");
                    }
                }
            }
            Component comp = get(key, placeholders);
            sender.sendMessage(comp);
        } catch (Throwable t) {
            try {
                sender.sendMessage(Component.text("§c[EasyMending] " + key));
            } catch (Throwable ignored) {
            }
        }
    }

    public String getRaw(String key) {
        return config != null ? config.getString(key, key) : key;
    }
}
