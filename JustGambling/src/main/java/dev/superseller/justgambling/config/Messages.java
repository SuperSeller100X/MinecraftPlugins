package dev.superseller.justgambling.config;

import dev.superseller.justgambling.util.Text;

import java.io.File;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/** Reloadable MiniMessage language file. */
public final class Messages {
    private final JavaPlugin plugin;
    private FileConfiguration yaml;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public Component component(String key, Map<String, ?> placeholders) {
        String prefix = yaml == null ? "" : yaml.getString("prefix", "");
        String body = yaml == null ? key : yaml.getString(key, key);
        return Text.parse(prefix + body, placeholders);
    }

    public Component rawComponent(String key, Map<String, ?> placeholders) {
        String body = yaml == null ? key : yaml.getString(key, key);
        return Text.parse(body, placeholders);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, ?> placeholders) {
        if (sender != null) {
            sender.sendMessage(component(key, placeholders));
        }
    }

    public String raw(String key, String fallback) {
        return yaml == null ? fallback : yaml.getString(key, fallback);
    }

    public List<String> list(String key) {
        return yaml == null ? List.of() : yaml.getStringList(key);
    }
}
