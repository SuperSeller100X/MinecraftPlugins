package dev.superseller.playerbank.config;

import dev.superseller.playerbank.PlayerBankPlugin;
import java.io.File;
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
    }

    public String raw(String key) {
        return yaml.getString(key, key);
    }

    public Component component(String key, Map<String, String> placeholders) {
        String prefix = yaml.getString("prefix", "");
        String body = yaml.getString(key, key);
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
