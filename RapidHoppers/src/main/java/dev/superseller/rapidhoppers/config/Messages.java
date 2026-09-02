package dev.superseller.rapidhoppers.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MiniMessage-backed message store. Every string lives in {@code messages.yml}
 * and supports {@code {placeholder}} substitution; lore strings are split on
 * {@code |} so a single YAML line can describe a multi-line item tooltip.
 */
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

    public String raw(String key) {
        return yaml == null ? key : yaml.getString(key, key);
    }

    public String prefix() {
        return raw("prefix");
    }

    public Component component(String key, Map<String, String> placeholders) {
        return mini.deserialize(prefix() + apply(raw(key), placeholders));
    }

    /** Same as {@link #component} but without the plugin prefix. */
    public Component plain(String key, Map<String, String> placeholders) {
        return mini.deserialize(apply(raw(key), placeholders));
    }

    public Component deserialize(String miniMessage) {
        return mini.deserialize(miniMessage);
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(component(key, placeholders));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void sendPlain(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(plain(key, placeholders));
    }

    public void sendHelp(CommandSender sender, String section) {
        List<String> lines = yaml == null ? List.of() : yaml.getStringList("help." + section);
        if (lines.isEmpty()) {
            sender.sendMessage(deserialize("<gray>RapidHoppers — see /rh help"));
            return;
        }
        for (String line : lines) {
            sender.sendMessage(deserialize(line));
        }
    }

    /** Splits a lore template on '|' and resolves placeholders per line. */
    public List<Component> lore(String key, Map<String, String> placeholders) {
        String value = apply(raw(key), placeholders);
        List<Component> out = new ArrayList<>();
        for (String line : Arrays.asList(value.split("\\|", -1))) {
            if (line.isEmpty()) {
                out.add(mini.deserialize("<reset>"));
            } else {
                out.add(mini.deserialize("<!italic><gray>" + line));
            }
        }
        return out;
    }

    public static String apply(String template, Map<String, String> placeholders) {
        String out = template;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                out = out.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return out;
    }
}
