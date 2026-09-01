package dev.superseller.justgambling.util;

import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/** Small text helper used for MiniMessage config and placeholder substitution. */
public final class Text {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component parse(String value) {
        return MINI.deserialize(value == null ? "" : value);
    }

    public static Component parse(String value, Map<String, ?> placeholders) {
        String result = value == null ? "" : value;
        if (placeholders != null) {
            for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
                String replacement = String.valueOf(entry.getValue() == null ? "" : entry.getValue());
                result = result.replace("{" + entry.getKey() + "}", replacement);
            }
        }
        return parse(result);
    }
}
