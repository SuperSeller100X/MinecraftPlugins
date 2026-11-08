package dev.superseller.veltryx.config;

import org.bukkit.plugin.java.JavaPlugin;
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
    // Earlier shipped templates used <lime>, which is not a MiniMessage named color.
    // Translate it at render time for existing custom files without rewriting them.
    private static final Pattern LEGACY_LIME = Pattern.compile("(?i)(?<!\\\\)<(/?)lime>");

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private volatile Map<String, String> templates = Map.of();

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Start with the bundled messages, then overlay the server's custom file.
     * Even an old/incomplete file (or a failed update merge) can never hide a
     * message supplied in the current jar. Existing translations win.
     */
    public void reload() {
        Map<String, String> defaults = new HashMap<>();
        try (java.io.InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                collect(bundled, defaults);
            } else {
                plugin.getLogger().severe("The bundled messages.yml is missing from the plugin jar.");
            }
        } catch (java.io.IOException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Could not read default messages.yml", e);
        }
        Map<String, String> custom = new HashMap<>();
        collect(YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml")), custom);
        templates = mergeTemplates(defaults, custom);
    }

    private static void collect(YamlConfiguration yaml, Map<String, String> destination) {
        for (String key : yaml.getKeys(true)) {
            if (yaml.isString(key)) destination.put(key, yaml.getString(key, ""));
        }
    }

    /** Merge is intentionally independent of disk I/O, to make the fallback testable. */
    public static Map<String, String> mergeTemplates(Map<String, String> defaults, Map<String, String> custom) {
        Map<String, String> combined = new HashMap<>(defaults);
        combined.putAll(custom);
        return Map.copyOf(combined);
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
        String out = LEGACY_LIME.matcher(raw).replaceAll("<$1green>");
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
