package dev.superseller.randomstructurechallenge.config;

import java.io.File;
import java.util.List;
import java.util.Map;

import dev.superseller.randomstructurechallenge.RandomStructureChallengePlugin;
import dev.superseller.randomstructurechallenge.util.Placeholders;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * MiniMessage messages loaded from {@code messages.yml}.
 */
public final class Messages {

    private final RandomStructureChallengePlugin plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private FileConfiguration yaml;

    public Messages(RandomStructureChallengePlugin plugin) {
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
        if (sender == null) {
            return;
        }
        String prefix = yaml.getString("prefix", "");
        String body = yaml.getString(key, key);
        sender.sendMessage(mini.deserialize(prefix + Placeholders.apply(body, placeholders)));
    }

    public void sendHelp(CommandSender sender) {
        List<String> lines = yaml.getStringList("help");
        if (lines.isEmpty()) {
            sender.sendMessage(mini.deserialize("<gold>RandomStructureChallenge</gold> <gray>— /challenge help</gray>"));
            return;
        }
        for (String line : lines) {
            sender.sendMessage(mini.deserialize(Placeholders.apply(line, Map.of())));
        }
    }

    public Component component(String key, Map<String, String> placeholders) {
        String body = yaml.getString(key, key);
        return mini.deserialize(Placeholders.apply(body, placeholders));
    }

    public Component raw(String miniMessage) {
        return mini.deserialize(miniMessage == null ? "" : miniMessage);
    }

    public String rawString(String key, String fallback) {
        return yaml.getString(key, fallback);
    }
}
