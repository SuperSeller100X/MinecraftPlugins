package dev.superseller.hourglass.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Small RFC-4180-style CSV writer with configurable line endings and an
 * optional UTF-8 BOM (Excel on Windows needs the BOM to read plain UTF-8).
 * Pure Java, so it is unit-testable and behaves identically on every OS.
 */
public final class CsvBuilder {

    /** Line ending selector, mirrors {@code export.line-endings}. */
    public enum Newline {
        /** {@code \n} — Linux and macOS convention. */
        LF("\n"),
        /** {@code \r\n} — Windows convention, what Excel expects. */
        CRLF("\r\n"),
        /** Whatever the host OS uses. */
        SYSTEM(System.lineSeparator());

        private final String text;

        Newline(String text) {
            this.text = text;
        }

        public String text() {
            return text;
        }

        public static Newline parse(String configured) {
            if (configured == null) {
                return SYSTEM;
            }
            return switch (configured.trim().toLowerCase(Locale.US)) {
                case "lf", "unix" -> LF;
                case "crlf", "windows" -> CRLF;
                default -> SYSTEM;
            };
        }
    }

    /** The UTF-8 byte-order mark, written as an escape so the source stays ASCII. */
    public static final char BOM = '\uFEFF';

    private final Newline newline;
    private final boolean bom;
    private final List<String> lines = new ArrayList<>();

    public CsvBuilder(Newline newline, boolean bom) {
        this.newline = newline == null ? Newline.SYSTEM : newline;
        this.bom = bom;
    }

    /** Appends one row. */
    public CsvBuilder row(Object... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells[i]));
        }
        lines.add(sb.toString());
        return this;
    }

    /** Appends one row from a list. */
    public CsvBuilder row(Collection<?> cells) {
        return row(cells.toArray());
    }

    /** Quotes and escapes one cell; {@code null} becomes an empty cell. */
    public static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        boolean needsQuotes = text.indexOf(',') >= 0 || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0
                || text.startsWith(" ") || text.endsWith(" ");
        if (!needsQuotes) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    /** The finished document; every row is terminated by the configured newline. */
    public String build() {
        StringBuilder sb = new StringBuilder();
        if (bom) {
            sb.append(BOM);
        }
        for (String line : lines) {
            sb.append(line).append(newline.text());
        }
        return sb.toString();
    }

    public int rowCount() {
        return lines.size();
    }
}
