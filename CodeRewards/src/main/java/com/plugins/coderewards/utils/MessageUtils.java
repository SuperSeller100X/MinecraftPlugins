package com.plugins.coderewards.utils;

import org.bukkit.ChatColor;

public class MessageUtils {
    
    public static String colorize(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    public static String stripColors(String message) {
        if (message == null) return "";
        return ChatColor.stripColor(colorize(message));
    }
    
    public static String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + " seconds";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            return minutes + " minute" + (minutes != 1 ? "s" : "") + 
                   (remainingSeconds > 0 ? " and " + remainingSeconds + " second" + (remainingSeconds != 1 ? "s" : "") : "");
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + " hour" + (hours != 1 ? "s" : "") + 
                   (minutes > 0 ? " and " + minutes + " minute" + (minutes != 1 ? "s" : "") : "");
        }
    }
}
