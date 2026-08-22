package dev.superseller.chestlock.message;

import dev.superseller.chestlock.ChestLockPlugin;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Immutable-at-runtime MiniMessage templates loaded from messages.yml. */
public final class Messages {
    private final ChestLockPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private volatile Map<String, String> templates = Map.of();

    public Messages(ChestLockPlugin plugin) {
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

    public void send(Player player, String key, Map<String, String> values) {
        player.sendMessage(prefixed(key, values));
    }

    public void send(Player player, String key) {
        player.sendMessage(prefixed(key));
    }
}
