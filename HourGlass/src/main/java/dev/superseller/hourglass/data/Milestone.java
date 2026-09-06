package dev.superseller.hourglass.data;

import java.util.List;
import java.util.Locale;

/**
 * One configured playtime milestone, e.g. "10 hours on the server".
 *
 * @param name      unique id used to remember who already got it
 * @param seconds   the threshold, in seconds of the configured metric
 * @param message   optional override message key (falls back to
 *                  {@code milestones.reached})
 * @param commands  console commands to run, {@code %player%} is replaced
 * @param sound     optional per-milestone sound key override
 * @param broadcast whether the whole server hears about it
 * @param title     whether the player also gets a screen title
 */
public record Milestone(String name, long seconds, String message, List<String> commands,
                        String sound, boolean broadcast, boolean title) {

    public boolean hasCommands() {
        return commands != null && !commands.isEmpty();
    }

    /** Hours this milestone represents, rounded to 2 decimals worth of text. */
    public String describe() {
        if (seconds % 3_600L == 0L) {
            return (seconds / 3_600L) + "h";
        }
        if (seconds % 86_400L == 0L) {
            return (seconds / 86_400L) + "d";
        }
        return seconds + "s";
    }

    /** Normalises a configured name so it can be stored in a set. */
    public static String normalizeName(String raw, long seconds) {
        if (raw == null || raw.isBlank()) {
            return "auto-" + seconds;
        }
        return raw.trim().toLowerCase(Locale.US).replaceAll("[^a-z0-9_.-]", "-");
    }
}
