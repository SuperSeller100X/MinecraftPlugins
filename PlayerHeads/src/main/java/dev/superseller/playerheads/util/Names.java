package dev.superseller.playerheads.util;

import java.util.regex.Pattern;

/**
 * Minecraft account name validation. The default pattern matches vanilla
 * account names: letters, digits and underscores, 1-16 characters.
 */
public final class Names {

    public static final String DEFAULT_PATTERN_STRING = "^[A-Za-z0-9_]{1,16}$";
    public static final Pattern DEFAULT_PATTERN = Pattern.compile(DEFAULT_PATTERN_STRING);

    private Names() {
    }

    /**
     * @param name    the name to check
     * @param pattern the pattern the name must fully match
     * @return true if the name is non-empty and fully matches the pattern
     */
    public static boolean isValid(String name, Pattern pattern) {
        return name != null && !name.isEmpty() && pattern.matcher(name).matches();
    }
}
