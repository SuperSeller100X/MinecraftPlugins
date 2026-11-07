package dev.superseller.veltryx.config;

import dev.superseller.veltryx.VeltryxPlugin;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
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
 * ban screen). Placeholders use the {@code {name}} style; their values are
 * always inserted unparsed, so player input can never inject MiniMessage tags.
 * A placeholder without a supplied value is stripped before rendering, so raw
 * braces such as {@code {reason}} can never leak into chat.
 *
 * <p>Messages are sent as-is — the plugin never prepends its own name.
 */
public final class Messages {

    /** Matches an unfilled placeholder like {@code {reason}}. */
    private static final Pattern UNFILLED = Pattern.compile("\\{[a-zA-Z0-9_-]+}");

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
        return renderRaw(templates.getOrDefault(key, "<red>Missing message: " + key + "</red>"), values);
    }

    public Component render(String key) {
        return render(key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> values) {
        sender.sendMessage(render(key, values));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    /** Sends a template that may contain {@code \n} as separate chat lines. */
    public void sendMultiline(CommandSender sender, String key, Map<String, String> values) {
        String raw = templates.getOrDefault(key, "<red>Missing message: " + key + "</red>");
        for (String line : raw.split("\n")) {
            sender.sendMessage(renderRaw(line, values));
        }
    }

    /**
     * Renders one template line. Each {@code {name}} placeholder is turned into
     * a generated MiniMessage placeholder tag whose value stays unparsed, so
     * formatting in values can never inject tags; any placeholder without a
     * value is removed.
     */
    private Component renderRaw(String raw, Map<String, String> values) {
        String out = raw;
        List<TagResolver> resolvers = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String tag = "vxfill" + (i++);
            out = out.replace("{" + entry.getKey() + "}", "<" + tag + ">");
            resolvers.add(Placeholder.unparsed(tag, entry.getValue() == null ? "" : entry.getValue()));
        }
        out = UNFILLED.matcher(out).replaceAll("");
        return miniMessage.deserialize(out, TagResolver.resolver(resolvers));
    }
}
