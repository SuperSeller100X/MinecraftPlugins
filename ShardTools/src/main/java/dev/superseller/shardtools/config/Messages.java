package dev.superseller.shardtools.config;

import java.io.File;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MiniMessage-based translation layer backed by messages.yml.
 * Placeholders are simple %name% token replacements before parsing.
 */
public final class Messages {

    private final JavaPlugin plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private FileConfiguration messages = new YamlConfiguration();
    private String prefix = "";

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.isFile()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
        prefix = messages.getString("prefix", "");
    }

    /** Renders a message key to a component, applying %token% replacements. */
    public Component get(String key, String... replacements) {
        return mini.deserialize(apply(prefix + raw(key, replacements)));
    }

    /** Renders without the prefix (for lore lines and titles). */
    public Component bare(String key, String... replacements) {
        return mini.deserialize(raw(key, replacements));
    }

    public String raw(String key, String... replacements) {
        String text = messages.getString(key, key);
        return apply(text, replacements);
    }

    public void send(CommandSender target, String key, String... replacements) {
        target.sendMessage(get(key, replacements));
    }

    public MiniMessage miniMessage() {
        return mini;
    }

    private static String apply(String text, String... replacements) {
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return text;
    }
}
