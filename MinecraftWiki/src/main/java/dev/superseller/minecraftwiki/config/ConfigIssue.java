package dev.superseller.minecraftwiki.config;

/**
 * One configuration problem found while loading or validating a file.
 *
 * <p>Nothing in this plugin is ever silently ignored: every problem is collected here,
 * logged with the file and path it came from, and counted in the startup summary.</p>
 */
public record ConfigIssue(Level level, String file, String path, String message) {

    public enum Level {
        INFO, WARNING, ERROR
    }

    public static ConfigIssue warning(String file, String path, String message) {
        return new ConfigIssue(Level.WARNING, file, path, message);
    }

    public static ConfigIssue error(String file, String path, String message) {
        return new ConfigIssue(Level.ERROR, file, path, message);
    }

    public static ConfigIssue info(String file, String path, String message) {
        return new ConfigIssue(Level.INFO, file, path, message);
    }

    /** Renders as {@code file:path message}, or {@code file message} when path is empty. */
    @Override
    public String toString() {
        String where = path == null || path.isEmpty() ? file : file + ":" + path;
        return where + " " + message;
    }
}
