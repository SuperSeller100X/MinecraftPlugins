package dev.superseller.hourglass.config;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads {@code messages.yml}, renders MiniMessage and substitutes
 * {@code {placeholder}} tokens.
 *
 * <p>Messages never throw: a missing key falls back to the packaged
 * {@code messages.yml}, and a broken tag degrades to plain text instead of
 * killing a command. Legacy {@code &} colour codes are supported as a courtesy
 * (see {@code legacy-color-codes}) and values are escaped by default, so a
 * player called {@code <red>} cannot inject formatting into someone else's GUI.
 */
public final class Messages {

    /** {@code &}-style code, e.g. {@code &a}, {@code &l}, {@code &r}. */
    private static final Pattern LEGACY = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    private YamlConfiguration yaml = new YamlConfiguration();
    private String prefix = "";
    private boolean legacyCodes = true;
    private boolean escapeValues = true;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** (Re)reads {@code messages.yml} from disk, seeded from the jar. */
    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            try {
                YamlIO.extract(plugin, "messages.yml", file.toPath());
            } catch (Exception e) {
                plugin.getLogger().warning("Could not write messages.yml: " + e.getMessage());
            }
        }
        YamlConfiguration loaded = YamlIO.loadDefaults(plugin, "messages.yml");
        this.yaml = loaded;
        this.prefix = loaded.getString("prefix", "&8\u00BB &7");
        this.legacyCodes = loaded.getBoolean("legacy-color-codes", true);
        this.escapeValues = loaded.getBoolean("escape-placeholder-values", true);
    }

    /** {@code true} when the key exists and is not an empty string. */
    public boolean has(String key) {
        return yaml.isString(key) && !yaml.getString(key, "").isEmpty();
    }

    /** Raw template for a key, with defaults applied, or {@code null}. */
    public String raw(String key) {
        String value = yaml.getString(key, null);
        return value == null || value.isEmpty() ? null : value;
    }

    /** Renders a key without the prefix. Never {@code null}. */
    public Component component(String key, Map<String, String> placeholders) {
        return render0(raw(key), placeholders, false);
    }

    /** Renders a key, prefixing it unless the key opts out. */
    public Component prefixed(String key, Map<String, String> placeholders) {
        return render0(raw(key), placeholders, true);
    }

    /** Renders arbitrary configured text (GUI titles, item names, lore). */
    public Component render(String text) {
        return render0(text, Map.of(), false);
    }

    /** Renders arbitrary configured text with placeholders applied. */
    public Component render(String text, Map<String, String> placeholders) {
        return render0(text, placeholders, false);
    }

    /**
     * Sends a prefixed chat message. A key configured as an empty string is
     * silently skipped, which is how admins mute individual messages.
     */
    public void send(CommandSender to, String key, Map<String, String> placeholders) {
        if (to == null || !has(key)) {
            return;
        }
        to.sendMessage(prefixed(key, placeholders));
    }

    /** {@link #send(CommandSender, String, Map)} without placeholders. */
    public void send(CommandSender to, String key) {
        send(to, key, Map.of());
    }

    /** Sends a multi-line message key (a YAML list) as chat lines. */
    public void sendList(CommandSender to, String key, Map<String, String> placeholders) {
        for (Component line : lines(key, placeholders, true)) {
            to.sendMessage(line);
        }
    }

    /** Sends an action bar message. */
    public void action(Player player, String key, Map<String, String> placeholders) {
        if (player == null || !has(key)) {
            return;
        }
        player.sendActionBar(component(key, placeholders));
    }

    /** Lore / help lines from a list key (or a single string key split on {@code \n}). */
    public List<Component> lines(String key, Map<String, String> placeholders, boolean withPrefix) {
        List<Component> out = new ArrayList<>();
        List<String> configured = yaml.isList(key) ? yaml.getStringList(key) : null;
        if (configured == null || configured.isEmpty()) {
            String single = raw(key);
            if (single == null) {
                return out;
            }
            configured = List.of(single.split("\n"));
        }
        for (String line : configured) {
            out.add(render0(line, placeholders, withPrefix && out.isEmpty()));
        }
        return out;
    }

    /** Lore lines for GUI items — never prefixed. */
    public List<Component> lore(List<String> lines, Map<String, String> placeholders) {
        List<Component> out = new ArrayList<>();
        if (lines == null) {
            return out;
        }
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            out.add(render0(line, placeholders, false));
        }
        return out;
    }

    /** Prefix, then legacy codes, then escaped placeholder values, then parse. */
    private Component render0(String template, Map<String, String> placeholders, boolean addPrefix) {
        if (template == null || template.isEmpty()) {
            return Component.empty();
        }
        String text = template;
        if (addPrefix && prefix != null && !prefix.isEmpty()) {
            text = prefix + text;
        }
        if (legacyCodes) {
            text = legacyToMiniMessage(text);
        }
        text = substitute(text, placeholders);
        try {
            return miniMessage.deserialize(text);
        } catch (RuntimeException e) {
            try {
                return legacy.deserialize(text);
            } catch (RuntimeException second) {
                return Component.text(text);
            }
        }
    }

    /**
     * Replaces {@code {token}} placeholders. Values are tag-escaped by default so
     * a player name such as {@code <red>} or {@code &c} can never inject
     * formatting; {@code escape-placeholder-values: false} turns that off.
     */
    private String substitute(String text, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty() || text.indexOf('{') < 0) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            if (escapeValues) {
                value = miniMessage.escapeTags(value);
            }
            result = result.replace("{" + entry.getKey() + "}", value);
        }
        return result;
    }

    /** Translates {@code &a}&l into MiniMessage tags so old files keep working. */
    static String legacyToMiniMessage(String text) {
        Matcher matcher = LEGACY.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        matcher.reset();
        StringBuilder sb = new StringBuilder(text.length() + 16);
        int last = 0;
        while (matcher.find()) {
            sb.append(text, last, matcher.start());
            sb.append(tagFor(Character.toLowerCase(matcher.group(1).charAt(0))));
            last = matcher.end();
        }
        sb.append(text, last, text.length());
        return sb.toString();
    }

    private static String tagFor(char code) {
        return switch (code) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> "";
        };
    }

    /**
     * Sends a message to every online player and to the console. Used by
     * milestone announcements; a missing or empty key is skipped entirely.
     */
    public void broadcast(String key, Map<String, String> placeholders) {
        if (!has(key)) {
            return;
        }
        Component component = prefixed(key, placeholders);
        for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            online.sendMessage(component);
        }
        org.bukkit.Bukkit.getConsoleSender().sendMessage(component);
    }

    /** Like {@link #broadcast(String, Map)} but only to players holding a permission. */
    public void broadcastPermission(String permission, String key, Map<String, String> placeholders) {
        if (!has(key)) {
            return;
        }
        Component component = prefixed(key, placeholders);
        int sent = 0;
        for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(permission)) {
                online.sendMessage(component);
                sent++;
            }
        }
        if (sent > 0) {
            org.bukkit.Bukkit.getConsoleSender().sendMessage(component);
        }
    }

    /** Builds a placeholder map from {@code key, value, key, value, ...}. */
    public static Map<String, String> ph(Object... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), String.valueOf(pairs[i + 1]));
        }
        return map;
    }
}
