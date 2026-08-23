package dev.superseller.randomstructurechallenge.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Structure id helpers that do not depend on the Paper API.
 */
public final class StructureKeys {

    public static final Pattern SAFE = Pattern.compile("[a-z0-9_.:\\-/]+");

    private StructureKeys() {
    }

    public static String normalize(String key) {
        if (key == null) {
            return "";
        }
        String value = key.toLowerCase(Locale.ROOT).trim();
        if (value.isEmpty()) {
            return "";
        }
        if (!value.contains(":")) {
            return "minecraft:" + value;
        }
        return value;
    }

    public static boolean safe(String key) {
        return key != null && SAFE.matcher(key).matches();
    }
}
