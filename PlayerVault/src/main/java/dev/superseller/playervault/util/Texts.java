package dev.superseller.playervault.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Text helpers: placeholder substitution, legacy colour conversion and MiniMessage
 * parsing.
 *
 * <p>Server owners routinely paste {@code &a}-style colour codes into config files,
 * so any {@code &} code is translated into its MiniMessage equivalent before the
 * string is parsed. Unknown {@code <tags>} are left as literal text because the
 * shared {@link MiniMessage} instance is non-strict.
 */
public final class Texts {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private Texts() {
    }

    /** The shared MiniMessage instance. */
    public static MiniMessage mini() {
        return MINI;
    }

    /** Applies {@code key -> value} replacements. Keys are matched literally. */
    public static String replace(String input, Map<String, String> values) {
        if (input == null || input.isEmpty() || values == null || values.isEmpty()) {
            return input;
        }
        String result = input;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    /** Parses a MiniMessage string into a component. Never throws. */
    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        try {
            return MINI.deserialize(legacyToMini(input));
        } catch (RuntimeException ex) {
            // A malformed tag must never break a command; show the raw text instead.
            return Component.text(input);
        }
    }

    /** Flattens a component to plain text, used for titles, action bars and the log. */
    public static String plain(Component component) {
        return component == null ? "" : PLAIN.serialize(component);
    }

    /**
     * Converts Bukkit-style {@code &} colour codes into MiniMessage tags.
     *
     * <p>Supports {@code &0-&9}, {@code &a-&f}, the format codes {@code &k &l &m &n &o},
     * the reset code {@code &r} and hex colours written as {@code &#rrggbb}.
     */
    public static String legacyToMini(String input) {
        if (input == null || input.indexOf('&') < 0) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current != '&' || i + 1 >= input.length()) {
                out.append(current);
                continue;
            }
            char code = Character.toLowerCase(input.charAt(i + 1));
            String hex = hexColour(input, i);
            if (hex != null) {
                out.append("<color:#").append(hex).append('>');
                i += 7;
                continue;
            }
            String tag = switch (code) {
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
                default -> null;
            };
            if (tag == null) {
                out.append(current);
                continue;
            }
            out.append(tag);
            i++;
        }
        return out.toString();
    }

    /** Reads a {@code &#rrggbb} sequence starting at {@code start}, or {@code null}. */
    private static String hexColour(String input, int start) {
        if (input.length() < start + 8 || input.charAt(start + 1) != '#') {
            return null;
        }
        String candidate = input.substring(start + 2, start + 8);
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean hexLetter = Character.toLowerCase(c) >= 'a' && Character.toLowerCase(c) <= 'f';
            if (!digit && !hexLetter) {
                return null;
            }
        }
        return candidate;
    }

    /** Builds a replacement map from {@code key, value, key, value, ...} pairs. */
    public static Map<String, String> map(String... replacements) {
        Map<String, String> values = new LinkedHashMap<>();
        if (replacements == null) {
            return values;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            values.put(replacements[i], replacements[i + 1]);
        }
        return values;
    }

    /** Joins a list of strings with newlines, used for lore blocks. */
    public static List<String> orEmpty(List<String> value) {
        return value == null ? List.of() : value;
    }
}
