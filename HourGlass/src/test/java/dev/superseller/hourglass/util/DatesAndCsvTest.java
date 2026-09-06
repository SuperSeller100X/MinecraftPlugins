package dev.superseller.hourglass.util;

import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dates and CSV are the two places where an unnoticed platform difference would
 * show up (a server admin exporting a CSV on Windows, a host whose zone has a
 * DST shift), so they are pinned here.
 */
class DatesAndCsvTest {

    /** 2024-01-01 00:00:00 in the zone the tests below use. */
    private static final long NEW_YEAR_UTC = 1_704_062_400_000L;

    @Test
    @DisplayName("a fixed zone renders the same text on every operating system")
    void fixedZone() {
        Dates dates = new Dates("yyyy-MM-dd HH:mm:ss", ZoneId.of("UTC"));
        assertEquals("2024-01-01 00:00:00", dates.format(NEW_YEAR_UTC));
        Dates berlin = new Dates("yyyy-MM-dd HH:mm:ss", ZoneId.of("Europe/Berlin"));
        assertEquals("2024-01-01 01:00:00", berlin.format(NEW_YEAR_UTC));
    }

    @Test
    @DisplayName("the pattern is honoured and an unset timestamp prints the empty text")
    void patternAndEmpty() {
        Dates dates = new Dates("dd.MM.yyyy", ZoneId.of("UTC"));
        assertEquals("01.01.2024", dates.format(NEW_YEAR_UTC));
        assertEquals("never", dates.format(0L, "never"));
        assertEquals("-", dates.format(-1L));
        assertEquals("-", dates.format(0L), "the no-arg form uses \"-\" so a GUI never shows an empty line");
    }

    @Test
    @DisplayName("a broken pattern degrades instead of breaking the server")
    void brokenPattern() {
        Dates dates = new Dates("{{{not a pattern", ZoneId.of("UTC"));
        String rendered = dates.format(NEW_YEAR_UTC);
        assertTrue(rendered.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
                "falls back to the ISO-ish default, got: " + rendered);
    }

    @Test
    @DisplayName("zone names are resolved leniently")
    void zones() {
        assertEquals(ZoneId.of("UTC"), Dates.resolveZone("UTC"));
        assertEquals(ZoneId.of("Etc/UTC"), Dates.resolveZone("etc/utc"));
        assertEquals(ZoneId.of("Europe/Berlin"), Dates.resolveZone("Europe/Berlin"));
        assertEquals(ZoneId.systemDefault(), Dates.resolveZone("Mars/Olympus_Mons"));
        assertEquals(ZoneId.systemDefault(), Dates.resolveZone(null));
        assertEquals(ZoneId.systemDefault(), Dates.resolveZone("  "));
    }

    @Test
    @DisplayName("relative time never goes negative")
    void secondsSince() {
        assertEquals(0L, Dates.secondsSince(1_000L, 1_000L));
        assertEquals(5L, Dates.secondsSince(1_000L, 6_000L));
        assertEquals(-1L, Dates.secondsSince(9_000L, 1_000L), "a future join is reported as unknown, never as negative");
        assertEquals(-1L, Dates.secondsSince(0L, 1_000L), "never seen");
    }

    @Test
    @DisplayName("CSV escaping covers commas, quotes, newlines and leading =")
    void csv() {
        CsvBuilder csv = new CsvBuilder(CsvBuilder.Newline.LF, false);
        csv.row("player", "name,with comma", "quote\"inside", "line\nbreak");
        String built = csv.build();
        assertTrue(built.startsWith("player,\"name,with comma\",\"quote\"\"inside\",\"line\nbreak\""), built);
        assertEquals("plain", CsvBuilder.escape("plain"));
        assertEquals("", CsvBuilder.escape(null));
        assertEquals(" leading", CsvBuilder.escape(" leading"), "padding is preserved by quoting");
        assertEquals(1, csv.rowCount());
    }

    @Test
    @DisplayName("headers and rows share one quoting rule")
    void headerAndRows() {
        CsvBuilder csv = new CsvBuilder(CsvBuilder.Newline.LF, false);
        csv.row((Object[]) new String[]{"uuid", "name", "note"});
        csv.row(List.of("abc", "Bob", "a: b"));
        String[] lines = csv.build().split("\n");
        assertEquals(2, lines.length);
        assertEquals("uuid,name,note", lines[0]);
        assertEquals("abc,Bob,a: b", lines[1]);
    }

    @Test
    @DisplayName("line endings follow the config, not the platform")
    void newlines() {
        CsvBuilder windows = new CsvBuilder(CsvBuilder.Newline.CRLF, false);
        windows.row("a", "b");
        assertTrue(windows.build().endsWith("\r\n"), "export.line-endings: CRLF");
        CsvBuilder mac = new CsvBuilder(CsvBuilder.Newline.CR, false);
        mac.row("a", "b");
        assertTrue(mac.build().endsWith("\r"));
        assertEquals(CsvBuilder.Newline.CRLF, CsvBuilder.Newline.parse("crlf"));
        assertEquals(CsvBuilder.Newline.CRLF, CsvBuilder.Newline.parse("windows"));
        assertEquals(CsvBuilder.Newline.LF, CsvBuilder.Newline.parse("lf"));
        assertEquals(CsvBuilder.Newline.LF, CsvBuilder.Newline.parse("unix"));
        assertEquals(CsvBuilder.Newline.SYSTEM, CsvBuilder.Newline.parse("whatever"));
        assertEquals(CsvBuilder.Newline.SYSTEM, CsvBuilder.Newline.parse(null));
        assertEquals(System.lineSeparator(), CsvBuilder.Newline.SYSTEM.text());
    }

    @Test
    @DisplayName("the BOM is written once, first")
    void bom() {
        CsvBuilder withBom = new CsvBuilder(CsvBuilder.Newline.LF, true);
        withBom.row("x");
        String text = withBom.build();
        assertEquals(CsvBuilder.BOM, text.charAt(0));
        assertEquals("\uFEFFx\n", text);
        CsvBuilder without = new CsvBuilder(CsvBuilder.Newline.LF, false);
        without.row("x");
        assertTrue(without.build().startsWith("x"));
    }
}
