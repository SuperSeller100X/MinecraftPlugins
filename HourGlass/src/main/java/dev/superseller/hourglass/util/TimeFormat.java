package dev.superseller.hourglass.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Pure, deterministic duration formatting and parsing.
 *
 * <p>This class deliberately has <b>no</b> Bukkit dependency so it can be unit
 * tested without a server and so that its output is byte-identical on Linux,
 * Windows and macOS (everything is done in {@link Locale#US} on ASCII).
 *
 * <p>Formatting follows {@code config.yml -> format}; parsing accepts the usual
 * human shorthand ({@code 90m}, {@code 1d2h}, {@code 2w}, {@code 1:30}).
 */
public final class TimeFormat {

    /** Output styles selectable via {@code format.style}. */
    public enum Style {
        /** {@code 1y 3d 4h} — the largest units, separated. */
        UNITS,
        /** {@code 1 year, 3 days, 4 hours} — spelled out. */
        WORDS,
        /** {@code 8772:04:00} — hours may exceed 24. */
        DIGITAL,
        /** {@code 1y3d4h} — no separators. */
        COMPACT,
        /** {@code 1d 04:00:00} — days plus a clock. */
        CLOCK,
        /** {@code 315817200} — raw seconds. */
        SECONDS
    }

    /**
     * Immutable formatting configuration.
     *
     * @param style          output style
     * @param maxUnits       how many units to show at most ({@code 0} = all)
     * @param includeSeconds whether seconds may appear at all
     * @param separator      text between units in {@link Style#UNITS} / {@link Style#COMPACT}
     * @param zeroText       text used for exactly zero seconds
     * @param year           comma separated unit names, first is short, last is the word
     * @param month          comma separated unit names
     * @param week           comma separated unit names
     * @param day            comma separated unit names
     * @param hour           comma separated unit names
     * @param minute         comma separated unit names
     * @param second         comma separated unit names
     * @param defaultUnit    unit assumed for a bare number when parsing
     */
    public record Settings(
            Style style,
            int maxUnits,
            boolean includeSeconds,
            String separator,
            String zeroText,
            String year,
            String month,
            String week,
            String day,
            String hour,
            String minute,
            String second,
            String defaultUnit) {

        /** Defaults matching the shipped {@code config.yml}. */
        public static Settings defaults() {
            return new Settings(Style.UNITS, 3, true, " ", "a brand new player",
                    "y,year,years", "mo,month,months", "w,week,weeks", "d,day,days",
                    "h,hour,hours", "m,minute,minutes", "s,second,seconds", "seconds");
        }
    }

    public static final long SECOND = 1L;
    public static final long MINUTE = 60L;
    public static final long HOUR = 3_600L;
    public static final long DAY = 86_400L;
    public static final long WEEK = 7L * DAY;
    public static final long MONTH = 30L * DAY;
    public static final long YEAR = 365L * DAY;

    private static final int MAX_INPUT_LENGTH = 64;

    private final Settings settings;

    public TimeFormat(Settings settings) {
        this.settings = settings == null ? Settings.defaults() : settings;
    }

    public Settings settings() {
        return settings;
    }

    /** Formats a duration with the configured number of units. Never {@code null}. */
    public String format(long totalSeconds) {
        return format(totalSeconds, settings.maxUnits());
    }

    /**
     * Formats a duration using at most {@code max} units ({@code 0} = all).
     * Handy for GUI lines and the action bar where space is tight.
     */
    public String format(long totalSeconds, int max) {
        long seconds = Math.max(0L, totalSeconds);
        if (seconds == 0L) {
            return settings.zeroText() == null ? "" : settings.zeroText();
        }
        return switch (settings.style()) {
            case SECONDS -> Long.toString(seconds);
            case DIGITAL -> digital(seconds, false);
            case CLOCK -> digital(seconds, true);
            case COMPACT -> join(pieces(seconds, max), "", true);
            case WORDS -> join(pieces(seconds, max), ", ", false);
            case UNITS -> join(pieces(seconds, max), nullSafeSeparator(), true);
        };
    }

    /** Short, always-safe rendering used for logs, CSV and the console. */
    public static String formatPlain(long totalSeconds) {
        long seconds = Math.max(0L, totalSeconds);
        if (seconds == 0L) {
            return "0s";
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (long[] pair : new long[][]{{YEAR, 'y'}, {DAY, 'd'}, {HOUR, 'h'}, {MINUTE, 'm'}, {SECOND, 's'}}) {
            long value = seconds / pair[0];
            if (value <= 0L) {
                continue;
            }
            seconds -= value * pair[0];
            if (shown > 0) {
                sb.append(' ');
            }
            sb.append(value).append((char) pair[1]);
            if (++shown >= 3) {
                break;
            }
        }
        return sb.toString();
    }

    private String nullSafeSeparator() {
        return settings.separator() == null ? " " : settings.separator();
    }

    /** One {@code 3d} / {@code 5 hours} piece of a formatted duration. */
    private record Piece(long amount, String shortName, String word) {
    }

    private List<Piece> pieces(long seconds, int max) {
        String[] specs = {settings.year(), settings.month(), settings.week(), settings.day(),
                settings.hour(), settings.minute(), settings.second()};
        long[] sizes = {YEAR, MONTH, WEEK, DAY, HOUR, MINUTE, SECOND};

        List<Piece> out = new ArrayList<>(7);
        long rest = seconds;
        for (int i = 0; i < sizes.length; i++) {
            boolean last = i == sizes.length - 1;
            if (last && !settings.includeSeconds() && !out.isEmpty()) {
                break; // truncate instead of rounding up: 119s is "1m", never "2m"
            }
            long value = rest / sizes[i];
            rest -= value * sizes[i];
            if (value == 0L) {
                continue; // drop zeros; leading and inner ones are noise
            }
            String[] names = names(specs[i], sizes[i]);
            out.add(new Piece(value, names[0], names[1]));
        }
        if (out.isEmpty()) {
            String[] names = names(settings.second(), SECOND);
            out.add(new Piece(seconds, names[0], names[1]));
        }
        if (max > 0 && out.size() > max) {
            out = new ArrayList<>(out.subList(0, max));
        }
        return out;
    }

    /** Splits a configured {@code "d,day,days"} spec into {@code [short, word]}. */
    private static String[] names(String spec, long unitSeconds) {
        if (spec == null || spec.isBlank()) {
            String fallback = unitSeconds >= DAY ? "d" : unitSeconds >= HOUR ? "h"
                    : unitSeconds >= MINUTE ? "m" : "s";
            return new String[]{fallback, fallback};
        }
        List<String> parts = new ArrayList<>();
        for (String part : spec.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        if (parts.isEmpty()) {
            return new String[]{"s", "s"};
        }
        String shortName = parts.get(0);
        String word = parts.size() > 1 ? parts.get(parts.size() - 1) : shortName;
        return new String[]{shortName, word};
    }

    private static String join(List<Piece> pieces, String separator, boolean useShortNames) {
        StringBuilder sb = new StringBuilder();
        // An empty separator means "compact": 3d4h5m, no space between the number
        // and the unit either.
        boolean spaced = separator == null || !separator.isEmpty();
        for (Piece piece : pieces) {
            if (!sb.isEmpty()) {
                sb.append(separator);
            }
            sb.append(piece.amount());
            if (spaced) {
                sb.append(' ');
            }
            if (useShortNames) {
                sb.append(piece.shortName());
            } else {
                sb.append(piece.amount() == 1L ? singular(piece.word()) : piece.word());
            }
        }
        return sb.toString();
    }

    private static String singular(String word) {
        if (word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + "y";
        }
        if (word.endsWith("ses") || word.endsWith("xes")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.endsWith("s") && word.length() > 1) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    private static String digital(long seconds, boolean withDays) {
        long days = seconds / DAY;
        long rest = seconds - days * DAY;
        long hours = rest / HOUR;
        rest -= hours * HOUR;
        long minutes = rest / MINUTE;
        long secs = rest - minutes * MINUTE;
        if (withDays && days > 0L) {
            return String.format(Locale.US, "%dd %02d:%02d:%02d", days, hours, minutes, secs);
        }
        long totalHours = withDays ? hours : days * 24L + hours;
        return String.format(Locale.US, "%d:%02d:%02d", totalHours, minutes, secs);
    }

    // ------------------------------------------------------------------ parse

    /**
     * Parses a human duration into whole seconds.
     *
     * <p>Accepted: {@code 3600}, {@code 3600s}, {@code 1h}, {@code 90m},
     * {@code 1d2h30m}, {@code 2 weeks}, {@code 1:30} (h:m),
     * {@code 1:02:03:04} (d:h:m:s). Case, whitespace and {@code and}/{@code +}
     * are ignored. The unit assumed for a bare number is configurable
     * ({@code format.parse.default-unit}).
     *
     * @return the duration in seconds, or empty when the input is not a duration
     */
    public static OptionalLong parse(String input) {
        return parse(input, "seconds");
    }

    /** Parses with an explicit fallback unit for bare numbers. */
    public static OptionalLong parse(String input, String bareNumberUnit) {
        if (input == null) {
            return OptionalLong.empty();
        }
        String raw = input.trim().toLowerCase(Locale.US);
        if (raw.isEmpty() || raw.length() > MAX_INPUT_LENGTH) {
            return OptionalLong.empty();
        }
        raw = raw.replace("+", " ").replace(",", " ").replace("_", " ").replace("and", " ");
        raw = raw.replaceAll("\\s+", " ").trim();
        if (raw.isEmpty()) {
            return OptionalLong.empty();
        }
        if (raw.contains(":")) {
            return parseClock(raw);
        }
        if (raw.matches("\\d+")) {
            long size = unitSeconds(bareNumberUnit);
            if (size <= 0L) {
                size = SECOND;
            }
            try {
                return OptionalLong.of(Math.multiplyExact(Long.parseLong(raw), size));
            } catch (NumberFormatException | ArithmeticException e) {
                return OptionalLong.of(Long.MAX_VALUE);
            }
        }

        long total = 0L;
        int index = 0;
        while (index < raw.length()) {
            while (index < raw.length() && raw.charAt(index) == ' ') {
                index++;
            }
            int numberStart = index;
            while (index < raw.length() && Character.isDigit(raw.charAt(index))) {
                index++;
            }
            if (index == numberStart) {
                return OptionalLong.empty();
            }
            long value;
            try {
                value = Long.parseLong(raw.substring(numberStart, index));
            } catch (NumberFormatException e) {
                return OptionalLong.empty();
            }
            while (index < raw.length() && raw.charAt(index) == ' ') {
                index++;
            }
            int unitStart = index;
            while (index < raw.length() && Character.isLetter(raw.charAt(index))) {
                index++;
            }
            long size = unitSeconds(raw.substring(unitStart, index));
            if (size <= 0L) {
                return OptionalLong.empty();
            }
            try {
                total = Math.addExact(total, Math.multiplyExact(value, size));
            } catch (ArithmeticException e) {
                return OptionalLong.of(Long.MAX_VALUE);
            }
        }
        return OptionalLong.of(total);
    }

    private static OptionalLong parseClock(String raw) {
        String[] parts = raw.split(":");
        if (parts.length < 2 || parts.length > 4) {
            return OptionalLong.empty();
        }
        long[] values = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty() || !part.matches("\\d{1,9}")) {
                return OptionalLong.empty();
            }
            values[i] = Long.parseLong(part);
        }
        long seconds = switch (parts.length) {
            case 2 -> values[0] * HOUR + values[1] * MINUTE;
            case 3 -> values[0] * HOUR + values[1] * MINUTE + values[2];
            default -> values[0] * DAY + values[1] * HOUR + values[2] * MINUTE + values[3];
        };
        return OptionalLong.of(seconds);
    }

    /** Maps a unit word or abbreviation to its length in seconds, {@code -1} if unknown. */
    public static long unitSeconds(String unit) {
        if (unit == null) {
            return -1L;
        }
        return switch (unit.trim().toLowerCase(Locale.US)) {
            case "s", "sec", "secs", "second", "seconds" -> SECOND;
            case "m", "min", "mins", "minute", "minutes" -> MINUTE;
            case "h", "hr", "hrs", "hour", "hours" -> HOUR;
            case "d", "day", "days" -> DAY;
            case "w", "wk", "wks", "week", "weeks" -> WEEK;
            case "mo", "mon", "month", "months" -> MONTH;
            case "y", "yr", "yrs", "year", "years" -> YEAR;
            default -> -1L;
        };
    }

    /** Convenience for callers that treat invalid input as zero. */
    public static long parseOrZero(String input) {
        return parse(input).orElse(0L);
    }

    /**
     * Parses using {@code format.parse.default-unit} as the unit for bare
     * numbers, which is what all commands should call.
     */
    public OptionalLong parseWithSettings(String input) {
        return parse(input, settings.defaultUnit());
    }

    /** Like {@link #parseWithSettings(String)} but {@code -1} for invalid input. */
    public long parseOrInvalid(String input) {
        return parseWithSettings(input).orElse(-1L);
    }
}
