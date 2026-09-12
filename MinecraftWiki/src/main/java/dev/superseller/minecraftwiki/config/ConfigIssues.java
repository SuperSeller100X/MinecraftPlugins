package dev.superseller.minecraftwiki.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Collects configuration problems and reports them once, grouped by file.
 */
public final class ConfigIssues {

    /** Hard cap so a pathological configuration cannot flood the console. */
    private static final int MAX_LOGGED_PER_FILE = 25;

    private final List<ConfigIssue> issues = new ArrayList<>();
    private int suppressed;

    public void add(ConfigIssue issue) {
        if (issue != null) {
            issues.add(issue);
        }
    }

    public void addAll(List<ConfigIssue> more) {
        if (more != null) {
            issues.addAll(more);
        }
    }

    public List<ConfigIssue> all() {
        return Collections.unmodifiableList(issues);
    }

    public int size() {
        return issues.size();
    }

    public long count(ConfigIssue.Level level) {
        return issues.stream().filter(issue -> issue.level() == level).count();
    }

    public boolean hasErrors() {
        return count(ConfigIssue.Level.ERROR) > 0;
    }

    /** Logs every collected issue, grouped per file, truncating pathological output. */
    public void report(Logger logger) {
        if (issues.isEmpty()) {
            return;
        }
        String currentFile = null;
        int printedForFile = 0;
        for (ConfigIssue issue : issues) {
            if (!issue.file().equals(currentFile)) {
                currentFile = issue.file();
                printedForFile = 0;
                logger.warning("[Wiki/Config] " + currentFile + ":");
            }
            if (printedForFile >= MAX_LOGGED_PER_FILE) {
                suppressed++;
                continue;
            }
            printedForFile++;
            String prefix = switch (issue.level()) {
                case ERROR -> "  ! " ;
                case WARNING -> "  ~ ";
                case INFO -> "  . ";
            };
            String detail = issue.path() == null || issue.path().isEmpty()
                    ? issue.message()
                    : issue.path() + " " + issue.message();
            logger.warning("[Wiki/Config]" + prefix + detail);
        }
        if (suppressed > 0) {
            logger.warning("[Wiki/Config] " + suppressed + " further problem(s) not shown "
                    + "(raise per-file logging by fixing the first entries)");
        }
    }
}
