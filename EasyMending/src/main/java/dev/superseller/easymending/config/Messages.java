package dev.superseller.easymending.config;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Manages plugin localization, MiniMessage tags, and legacy color code formatting.
 */
public final class Messages {

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();
    private FileConfiguration config;
    private String prefix = "<gradient:#4facfe:#00f2fe><b>EasyMending</b></gradient> <dark_gray>»</dark_gray> ";

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        this.prefix = config.getString("prefix", prefix);
    }

    /**
     * Resolves a raw string template with supplied placeholder replacements.
     * Supports both MiniMessage syntax and legacy ampersand (&) color codes.
     *
     * @param key message key
     * @param placeholders key-value pairs
     * @return formatted Component
     */
    public Component get(String key, Map<String, String> placeholders) {
        String raw = config != null ? config.getString(key, "<red>Missing message: " + key + "</red>") : key;
        raw = raw.replace("<prefix>", prefix);

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace("<" + entry.getKey() + ">", entry.getValue());
                raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        // Convert legacy color codes to MiniMessage tags for seamless gradient and tag compatibility
        if (raw.contains("&")) {
            raw = convertAmpersandToMiniMessage(raw);
        }

        try {
            return miniMessage.deserialize(raw);
        } catch (Exception e) {
            return legacySerializer.deserialize(raw);
        }
    }

    /**
     * Translates legacy color and format codes into standard MiniMessage tags.
     */
    public static String convertAmpersandToMiniMessage(String text) {
        if (text == null || !text.contains("&")) {
            return text;
        }
        return text
                .replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("&k", "<obfuscated>")
                .replace("&l", "<bold>")
                .replace("&m", "<strikethrough>")
                .replace("&n", "<underlined>")
                .replace("&o", "<italic>")
                .replace("&r", "<reset>");
    }

    /**
     * Sends a localized message to a CommandSender with optional placeholder replacements.
     *
     * @param sender target recipient
     * @param key message key
     * @param placeholderPairs alternating key and value strings
     */
    public void send(CommandSender sender, String key, String... placeholderPairs) {
        if (sender == null) return;
        Map<String, String> placeholders = new HashMap<>();
        for (int i = 0; i < placeholderPairs.length - 1; i += 2) {
            placeholders.put(placeholderPairs[i], placeholderPairs[i + 1]);
        }
        sender.sendMessage(get(key, placeholders));
    }

    public String getRaw(String key) {
        return config != null ? config.getString(key, key) : key;
    }
}
