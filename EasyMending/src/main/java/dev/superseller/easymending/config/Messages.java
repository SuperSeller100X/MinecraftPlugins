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
 * Manages plugin localization and MiniMessage-based message formatting.
 */
public final class Messages {

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
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
     *
     * @param key message key
     * @param placeholders key-value pairs
     * @return MiniMessage formatted Component
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

        // Support both MiniMessage and legacy section/ampersand color codes
        if (raw.contains("&") || raw.contains("§")) {
            raw = LegacyComponentSerializer.legacyAmpersand().deserialize(raw).toString();
        }

        try {
            return miniMessage.deserialize(raw);
        } catch (Exception e) {
            return Component.text(raw);
        }
    }

    /**
     * Sends a localized message to a CommandSender with optional placeholder replacements.
     *
     * @param sender target recipient
     * @param key message key
     * @param placeholderPairs alternating key and value strings (e.g. "player", "Steve", "cost", "10")
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
