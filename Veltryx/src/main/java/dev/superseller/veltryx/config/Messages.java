package dev.superseller.veltryx.config;

import dev.superseller.veltryx.VeltryxPlugin;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Immutable-at-runtime MiniMessage templates loaded from messages.yml.
 *
 * <p>Templates may contain {@code \n} to span multiple chat lines (used by the
 * ban screen). Placeholders use the {@code {name}} style and are always
 * inserted unparsed, so player input can never inject MiniMessage tags.
 */
public final class Messages {
    private final VeltryxPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private volatile Map<String, String> templates = Map.of();

    public Messages(VeltryxPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, String> loaded = new HashMap<>();
        for (String key : yaml.getKeys(true)) {
            if (yaml.isString(key)) {
                loaded.put(key, yaml.getString(key, ""));
            }
        }
        templates = Map.copyOf(loaded);
    }

    public Component render(String key, Map<String, String> values) {
        String raw = templates.getOrDefault(key, "<red>Missing message: " + key + "</red>");
        List<TagResolver> resolvers = values.entrySet().stream()
                .map(entry -> Placeholder.unparsed(entry.getKey(), entry.getValue()))
                .map(TagResolver.class::cast)
                .toList();
        return miniMessage.deserialize(raw, TagResolver.resolver(resolvers));
    }

    public Component render(String key) {
        return render(key, Map.of());
    }

    public Component prefixed(String key, Map<String, String> values) {
        return render("prefix").append(render(key, values));
    }

    public Component prefixed(String key) {
        return prefixed(key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> values) {
        sender.sendMessage(prefixed(key, values));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    /** Sends a template that may contain {@code \n} as separate chat lines. */
    public void sendMultiline(CommandSender sender, String key, Map<String, String> values) {
        String raw = templates.getOrDefault(key, "<red>Missing message: " + key + "</red>");
        String[] lines = raw.split("\n");
        Map<String, String> all = new HashMap<>(values);
        for (int i = 0; i < lines.length; i++) {
            sender.sendMessage(renderLine(lines[i], all));
        }
    }

    private Component renderLine(String raw, Map<String, String> values) {
        List<TagResolver> resolvers = values.entrySet().stream()
                .map(entry -> Placeholder.unparsed(entry.getKey(), entry.getValue()))
                .map(TagResolver.class::cast)
                .toList();
        return miniMessage.deserialize(raw, TagResolver.resolver(resolvers));
    }
}
