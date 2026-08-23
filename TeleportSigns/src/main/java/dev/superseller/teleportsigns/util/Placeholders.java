package dev.superseller.teleportsigns.util;

import java.util.Map;

/**
 * Simple {@code {placeholder}} substitution used before MiniMessage parsing.
 */
public final class Placeholders {

    private Placeholders() {
    }

    public static String apply(String template, Map<String, String> placeholders) {
        if (template == null) {
            return "";
        }
        if (placeholders == null || placeholders.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            result = result.replace("{" + entry.getKey() + "}", value);
        }
        return result;
    }
}
