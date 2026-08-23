package dev.superseller.connectedtools.util;

import org.bukkit.ChatColor;

public final class Colors {

    private Colors() {}

    public static String parse(String input) {
        if (input == null) return "";
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    public static String strip(String input) {
        return ChatColor.stripColor(parse(input));
    }
}
