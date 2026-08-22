package dev.superseller.chunkvoter.config;

import dev.superseller.chunkvoter.ChunkVoterPlugin;

import java.io.File;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * MiniMessage-based message loading from messages.yml. Placeholders use the
 * {@code {name}} syntax and are substituted before MiniMessage parsing.
 */
public final class Messages {

    private final ChunkVoterPlugin plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private FileConfiguration yaml;

    public Messages(ChunkVoterPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public String raw(String key) {
        return yaml.getString(key, key);
    }

    public boolean has(String key) {
        return yaml.contains(key);
    }

    public List<String> stringList(String key) {
        return yaml.getStringList(key);
    }

    public Component component(String key, Map<String, String> placeholders) {
        String prefix = yaml.getString("prefix", "");
        String body = yaml.getString(key, key);
        return mini.deserialize(prefix + apply(body, placeholders));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(component(key, placeholders));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public Component deserialize(String mini) {
        return this.mini.deserialize(mini);
    }

    public void sendHelp(CommandSender sender, String section) {
        List<String> lines = yaml.getStringList("help." + section);
        if (lines.isEmpty()) {
            sender.sendMessage(deserialize("<gold>" + plugin.getName() + "</gold> <gray>run /cv for help</gray>"));
            return;
        }
        for (String line : lines) {
            sender.sendMessage(deserialize(line));
        }
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
