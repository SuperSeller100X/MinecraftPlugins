package dev.superseller.playerheads.util;

import java.util.Map;

/**
 * Simple {@code {placeholder}} substitution used before MiniMessage parsing.
 */
public final class Placeholders {

    private Placeholders() {
    }

    /**
     * Replaces every {@code {key}} occurrence with the mapped value.
     *
     * @param template     message template
     * @param placeholders key/value pairs, may be empty
     * @return the substituted string
     */
    public static String apply(String template, Map<String, String> placeholders) {
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
