package dev.superseller.playerheads.config;

import dev.superseller.playerheads.PlayerHeadsPlugin;
import dev.superseller.playerheads.util.Placeholders;

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

    private final PlayerHeadsPlugin plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private FileConfiguration yaml;

    public Messages(PlayerHeadsPlugin plugin) {
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
        String prefix = yaml.getString("prefix", "");
        String body = yaml.getString(key, key);
        sender.sendMessage(mini.deserialize(prefix + Placeholders.apply(body, placeholders)));
    }

    public void sendHelp(CommandSender sender) {
        List<String> lines = yaml.getStringList("help");
        if (lines.isEmpty()) {
            sender.sendMessage(mini.deserialize("<gold>PlayerHeads</gold> <gray>— /playerheads <player> [amount]</gray>"));
            return;
        }
        for (String line : lines) {
            sender.sendMessage(mini.deserialize(Placeholders.apply(line, Map.of())));
        }
    }

    public Component deserialize(String miniMessage) {
        return mini.deserialize(miniMessage);
    }
}
