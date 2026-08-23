package dev.superseller.connectedtools.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class PluginSettings {

    private final JavaPlugin plugin;
    private YamlConfiguration config;
    private YamlConfiguration messages;

    public PluginSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        File msgFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!msgFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(msgFile);
    }

    public String msg(String path) {
        String value = messages.getString(path, "&c[Missing message: " + path + "]");
        return dev.superseller.connectedtools.util.Colors.parse(value);
    }

    public List<String> msgList(String path) {
        List<String> list = messages.getStringList(path);
        if (list == null || list.isEmpty()) {
            return Collections.singletonList(msg(path));
        }
        return list;
    }

    public String prefix() {
        String raw = messages.getString("prefix", "&8[&dConnectedTools&8] &r");
        return dev.superseller.connectedtools.util.Colors.parse(raw);
    }

    public int pulseDurationTicks() {
        return config.getInt("pulse-duration-ticks", 2);
    }

    public int maxConnectionsPerPlayer() {
        return config.getInt("max-connections", 50);
    }

    public boolean notifications() {
        return config.getBoolean("notifications", true);
    }

    public int maxBindDistance() {
        return config.getInt("max-bind-distance", 10);
    }

    public boolean sounds() {
        return config.getBoolean("sounds", true);
    }
}
