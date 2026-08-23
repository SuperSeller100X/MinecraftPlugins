package dev.superseller.randomstructurechallenge.util;

/**
 * Pure helpers for the hotbar / BossBar countdown visuals.
 */
public final class TimerBar {

    public static final int DEFAULT_WIDTH = 10;

    private TimerBar() {
    }

    public static String clock(int seconds) {
        int value = Math.max(0, seconds);
        int minutes = value / 60;
        int rest = value % 60;
        return String.format("%02d:%02d", minutes, rest);
    }

    /**
     * Remaining time as an integer percent in {@code [0, 100]}.
     * A 100-second interval drops exactly 1% per second.
     */
    public static int percent(int remaining, int interval) {
        if (interval <= 0) {
            return 0;
        }
        int clamped = Math.max(0, Math.min(remaining, interval));
        return (int) Math.round((clamped * 100.0) / interval);
    }

    /**
     * BossBar progress in {@code [0, 1]}.
     */
    public static float progress(int remaining, int interval) {
        if (interval <= 0) {
            return 0f;
        }
        int clamped = Math.max(0, Math.min(remaining, interval));
        return clamped / (float) interval;
    }

    public static String bar(int remaining, int interval) {
        return bar(remaining, interval, DEFAULT_WIDTH);
    }

    public static String bar(int remaining, int interval, int width) {
        int size = Math.max(1, width);
        int filled = filledBlocks(remaining, interval, size);
        StringBuilder builder = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            builder.append(i < filled ? '▰' : '▱');
        }
        return builder.toString();
    }

    public static int filledBlocks(int remaining, int interval, int width) {
        int size = Math.max(1, width);
        if (interval <= 0) {
            return 0;
        }
        int clamped = Math.max(0, Math.min(remaining, interval));
        return (int) Math.round((clamped * (double) size) / interval);
    }

    public static String prettyStructure(String key) {
        if (key == null || key.isBlank()) {
            return "unknown";
        }
        String name = key;
        int colon = name.indexOf(':');
        if (colon >= 0 && colon + 1 < name.length()) {
            name = name.substring(colon + 1);
        }
        String[] parts = name.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.isEmpty() ? key : builder.toString();
    }
}
