package dev.superseller.playerbank.config;

import dev.superseller.playerbank.PlayerBankPlugin;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class Messages {

    private final PlayerBankPlugin plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private FileConfiguration yaml;

    public Messages(PlayerBankPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        // Fall back to the bundled messages.yml so keys added in newer versions
        // still resolve on servers that keep an older, user-edited file.
        InputStream bundled = plugin.getResource("messages.yml");
        if (bundled != null) {
            try (InputStreamReader reader = new InputStreamReader(bundled, StandardCharsets.UTF_8)) {
                yaml.setDefaults(YamlConfiguration.loadConfiguration(reader));
            } catch (java.io.IOException ignored) {
                // Defaults are a nicety; missing keys just echo their name.
            }
        }
    }

    /** Looks up a message in the user's file, then the bundled defaults, then echoes the key. */
    public String raw(String key) {
        String value = yaml.getString(key);
        return value != null ? value : key;
    }

    public Component component(String key, Map<String, String> placeholders) {
        String prefix = yaml.getString("prefix");
        if (prefix == null) {
            prefix = "";
        }
        String body = raw(key);
        String combined = prefix + apply(body, placeholders);
        return mini.deserialize(combined);
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(component(key, placeholders));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void sendHelp(CommandSender sender) {
        List<String> lines = yaml.getStringList("help");
        if (lines.isEmpty()) {
            sender.sendMessage(mini.deserialize("<gold>/bank help"));
            return;
        }
        for (String line : lines) {
            sender.sendMessage(mini.deserialize(line));
        }
    }

    public List<Component> helpLines() {
        List<Component> out = new ArrayList<>();
        for (String line : yaml.getStringList("help")) {
            out.add(mini.deserialize(line));
        }
        return out;
    }

    public Component deserialize(String miniMessage) {
        return mini.deserialize(miniMessage);
    }

    public static String apply(String template, Map<String, String> placeholders) {
        String s = template;
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                s = s.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return s;
    }
}
