package dev.superseller.teleportsigns.config;

import dev.superseller.teleportsigns.util.Placeholders;

import java.io.File;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** MiniMessage messages loaded from {@code messages.yml}. */
public final class Messages {

    private final JavaPlugin plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();
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

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        if (sender == null || yaml == null) {
            return;
        }
        String prefix = yaml.getString("prefix", "");
        String body = yaml.getString(key, key);
        sender.sendMessage(mini.deserialize(Placeholders.apply(prefix + body, placeholders)));
    }

    public String raw(String key, String fallback) {
        if (yaml == null) {
            return fallback;
        }
        return yaml.getString(key, fallback);
    }

    public void sendHelp(CommandSender sender) {
        if (sender == null || yaml == null) {
            return;
        }
        List<String> lines = yaml.getStringList("help");
        if (lines.isEmpty()) {
            sender.sendMessage(mini.deserialize("<gold>TeleportSigns</gold> <gray>/ts [world] x y z</gray>"));
            return;
        }
        for (String line : lines) {
            sender.sendMessage(mini.deserialize(line));
        }
    }
}
