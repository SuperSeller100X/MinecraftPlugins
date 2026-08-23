package dev.superseller.chestlock.util;

/** Compact player-facing duration formatting. */
public final class DurationFormatter {
    private DurationFormatter() {
    }

    public static String formatMillis(long millis) {
        long totalSeconds = Math.max(0, (millis + 999) / 1_000);
        long hours = totalSeconds / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;
        StringBuilder result = new StringBuilder();
        if (hours > 0) {
            result.append(hours).append('h');
        }
        if (minutes > 0) {
            if (!result.isEmpty()) result.append(' ');
            result.append(minutes).append('m');
        }
        if (seconds > 0 || result.isEmpty()) {
            if (!result.isEmpty()) result.append(' ');
            result.append(seconds).append('s');
        }
        return result.toString();
    }
}
