package dev.superseller.connectedtools.util;

import java.util.Map;

public final class Text {

    private Text() {}

    public static String apply(String template, Map<String, String> vars) {
        if (template == null) return "";
        String result = template;
        if (vars != null) {
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                String key = "{" + entry.getKey() + "}";
                String value = entry.getValue() == null ? "" : entry.getValue();
                result = result.replace(key, value);
            }
        }
        return result;
    }
}
