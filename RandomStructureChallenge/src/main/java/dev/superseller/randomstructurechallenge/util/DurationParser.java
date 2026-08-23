package dev.superseller.randomstructurechallenge.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses challenge intervals typed in chat or as a command argument.
 *
 * <p>Accepted forms: {@code 60}, {@code 90s}, {@code 1m}, {@code 1m30s},
 * {@code 1:30}, {@code 1 minute}, {@code 2 hours}.</p>
 */
public final class DurationParser {

    private static final Pattern CLOCK = Pattern.compile("^(\\d+):(\\d{1,2})(?::(\\d{1,2}))?$");
    private static final Pattern TOKEN = Pattern.compile("^(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?$");
    private static final Pattern DIGITS = Pattern.compile("^\\d+$");

    private DurationParser() {
    }

    public record Result(boolean ok, int seconds, String errorKey, String input) {
        public static Result success(int seconds) {
            return new Result(true, seconds, null, "");
        }

        public static Result error(String errorKey, String input) {
            return new Result(false, 0, errorKey, input == null ? "" : input);
        }
    }

    public static Result parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Result.error("invalid-interval", raw == null ? "" : raw);
        }
        String original = raw.trim();
        String normalized = original.toLowerCase(Locale.ROOT).trim();

        Matcher clock = CLOCK.matcher(normalized);
        if (clock.matches()) {
            try {
                if (clock.group(3) == null) {
                    int minutes = Integer.parseInt(clock.group(1));
                    int seconds = Integer.parseInt(clock.group(2));
                    if (seconds >= 60) {
                        return Result.error("invalid-interval", original);
                    }
                    return bounded(Math.addExact(Math.multiplyExact(minutes, 60), seconds), original);
                }
                int hours = Integer.parseInt(clock.group(1));
                int minutes = Integer.parseInt(clock.group(2));
                int seconds = Integer.parseInt(clock.group(3));
                if (minutes >= 60 || seconds >= 60) {
                    return Result.error("invalid-interval", original);
                }
                int total = Math.addExact(Math.multiplyExact(hours, 3600), Math.multiplyExact(minutes, 60));
                return bounded(Math.addExact(total, seconds), original);
            } catch (ArithmeticException | NumberFormatException ex) {
                return Result.error("interval-too-high", original);
            }
        }

        if (DIGITS.matcher(normalized).matches()) {
            try {
                return bounded(Integer.parseInt(normalized), original);
            } catch (NumberFormatException ex) {
                return Result.error("interval-too-high", original);
            }
        }

        String compacted = compactUnits(normalized);
        if (DIGITS.matcher(compacted).matches()) {
            try {
                return bounded(Integer.parseInt(compacted), original);
            } catch (NumberFormatException ex) {
                return Result.error("interval-too-high", original);
            }
        }

        Matcher tokens = TOKEN.matcher(compacted);
        if (tokens.matches() && (tokens.group(1) != null || tokens.group(2) != null || tokens.group(3) != null)) {
            try {
                int total = 0;
                if (tokens.group(1) != null) {
                    total = Math.addExact(total, Math.multiplyExact(Integer.parseInt(tokens.group(1)), 3600));
                }
                if (tokens.group(2) != null) {
                    total = Math.addExact(total, Math.multiplyExact(Integer.parseInt(tokens.group(2)), 60));
                }
                if (tokens.group(3) != null) {
                    total = Math.addExact(total, Integer.parseInt(tokens.group(3)));
                }
                return bounded(total, original);
            } catch (ArithmeticException | NumberFormatException ex) {
                return Result.error("interval-too-high", original);
            }
        }

        return Result.error("invalid-interval", original);
    }

    /**
     * Turns {@code 1 minute 30 seconds} / {@code 1min} into {@code 1m30s}.
     */
    static String compactUnits(String lower) {
        String s = lower;
        s = replaceWord(s, "hours", "h");
        s = replaceWord(s, "hour", "h");
        s = replaceWord(s, "hrs", "h");
        s = replaceWord(s, "hr", "h");
        s = replaceWord(s, "minutes", "m");
        s = replaceWord(s, "minute", "m");
        s = replaceWord(s, "mins", "m");
        s = replaceWord(s, "min", "m");
        s = replaceWord(s, "seconds", "s");
        s = replaceWord(s, "second", "s");
        s = replaceWord(s, "secs", "s");
        s = replaceWord(s, "sec", "s");
        return s.replaceAll("\\s+", "");
    }

    private static String replaceWord(String input, String word, String unit) {
        return input.replace(word, unit);
    }

    private static Result bounded(int seconds, String original) {
        if (seconds <= 0) {
            return Result.error("interval-too-low", original);
        }
        return Result.success(seconds);
    }
}
