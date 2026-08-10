package com.plugins.giftsystem.utils;

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
    
    public static String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        if (seconds < 60) {
            return seconds + " seconds";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            return minutes + " minute" + (minutes != 1 ? "s" : "") + 
                   (remainingSeconds > 0 ? " and " + remainingSeconds + " second" + (remainingSeconds != 1 ? "s" : "") : "");
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + " hour" + (hours != 1 ? "s" : "") + 
                   (minutes > 0 ? " and " + minutes + " minute" + (minutes != 1 ? "s" : "") : "");
        } else {
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            return days + " day" + (days != 1 ? "s" : "") + 
                   (hours > 0 ? " and " + hours + " hour" + (hours != 1 ? "s" : "") : "");
        }
    }
}
