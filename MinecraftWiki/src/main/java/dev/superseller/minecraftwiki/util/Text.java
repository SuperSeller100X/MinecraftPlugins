package dev.superseller.minecraftwiki.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * MiniMessage rendering and identifier formatting.
 *
 * <p>Every user facing string in this plugin passes through here, so MiniMessage is
 * supported uniformly in {@code messages.yml}, {@code gui.yml}, {@code categories.yml}
 * and {@code articles.yml}.</p>
 */
public final class Text {

    /** Shared, immutable and thread safe MiniMessage instance. */
    public static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private Text() {
    }

    /** Deserialises a MiniMessage string. Null or blank input yields an empty component. */
    public static Component mini(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return MINI.deserialize(input);
    }

    /** Deserialises a MiniMessage string, substituting {@code <key>} placeholders. */
    public static Component mini(String input, Map<String, String> placeholders) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        if (placeholders == null || placeholders.isEmpty()) {
            return MINI.deserialize(input);
        }
        List<TagResolver> tags = new ArrayList<>(placeholders.size());
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            tags.add(Placeholder.unparsed(entry.getKey(), entry.getValue() == null ? "" : entry.getValue()));
        }
        return MINI.deserialize(input, tags.toArray(new TagResolver[0]));
    }

    /** Replaces {@code <key>} placeholders in a raw string without rendering it. */
    public static String applyPlaceholders(String input, Map<String, String> placeholders) {
        if (input == null || input.isEmpty() || placeholders == null || placeholders.isEmpty()) {
            return input;
        }
        String out = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            out = out.replace("<" + entry.getKey() + ">", value);
        }
        return out;
    }

    /** Flattens a component to unformatted text (used for the search index). */
    public static String plain(Component component) {
        return component == null ? "" : PLAIN.serialize(component);
    }

    /**
     * Turns a registry identifier into a readable title.
     * {@code diamond_sword} becomes {@code Diamond Sword}; {@code minecraft:oak_planks}
     * becomes {@code Oak Planks}.
     */
    public static String prettify(String id) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        String key = id;
        int colon = key.indexOf(':');
        if (colon >= 0) {
            key = key.substring(colon + 1);
        }
        String[] parts = key.replace('.', '_').replace('-', '_').split("_");
        StringBuilder out = new StringBuilder(key.length() + parts.length);
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    /** Lower-cases for case-insensitive matching using the root locale (Turkish-safe). */
    public static String fold(String input) {
        if (input == null) {
            return "";
        }
        // Trimming and collapsing runs of whitespace is what lets "diamond  sword" from a command
        // line match the title "Diamond Sword", and keeps ids, titles and queries comparable.
        return input.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * Drops a namespaced-key prefix, so {@code minecraft:diamond} and {@code mymod:thing} become
     * {@code diamond} and {@code thing}.
     *
     * <p>This is a lookup normaliser, not a display formatter: every map in the snapshot is keyed
     * by the bare name, so both vanilla and plugin keys have to be reduced the same way to find
     * their entry. Never use it to build text a player reads - a stripped plugin namespace would
     * be ambiguous.</p>
     */
    public static String stripNamespace(String id) {
        if (id == null) {
            return null;
        }
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    /** Joins strings with a separator, skipping nulls and blanks. */
    public static String join(List<String> values, String separator) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(separator);
            }
            out.append(value);
        }
        return out.toString();
    }

    /**
     * Strips anything that looks like a MiniMessage tag from player supplied text.
     *
     * <p>Values typed by a player are inserted into templates with
     * {@link #applyPlaceholders} so that highlighting can be applied around them, which means
     * they must not be able to smuggle their own tags in. Anything wrapped in angle brackets
     * is removed; the plain words survive.</p>
     */
    public static String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return input.replaceAll("<[^>]*>", "").trim();
    }

    /** Clamps a value into {@code [min, max]}. */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
