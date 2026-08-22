package dev.superseller.subscriptions.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Text {

    private Text() {
    }

    public static String apply(String template, Map<String, String> placeholders) {
        if (template == null) {
            return "";
        }
        String out = template;
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return Colors.parse(out);
    }

    public static List<String> apply(List<String> lines, Map<String, String> placeholders) {
        List<String> out = new ArrayList<>();
        if (lines == null) {
            return out;
        }
        for (String line : lines) {
            out.add(apply(line, placeholders));
        }
        return out;
    }

    public static String join(List<String> parts, String sep) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(sep);
            }
            out.append(part);
        }
        return out.toString();
    }
}
