package dev.superseller.attag.smoke;

import dev.superseller.attag.engine.PingEngine;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Offline unit tests for the pure mention engine (no Bukkit involved).
 * Run by build.sh — exit code 1 on any failure.
 */
public final class EngineTest {

    private static final List<String> ONLINE = Arrays.asList("Alex", "Bob", "Carol");

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        t("single mention", PingEngine.parse("@Bob hello", ONLINE, "Alex", 1, 2, 3),
                "@Bob hello", set("Bob"), set());
        t("offline name ignored", PingEngine.parse("@Dave hi", ONLINE, "Alex", 1, 2, 3),
                "@Dave hi", set(), set());
        t("@here replaced, no pings", PingEngine.parse("meet @here ok", ONLINE, "Alex", 100, 64, -7),
                "meet [100, 64, -7] ok", set(), set());
        t("@everyone", PingEngine.parse("@everyone hi", ONLINE, "Alex", 1, 2, 3),
                "@everyone hi", set(), set("Bob", "Carol"));
        t("@all alias", PingEngine.parse("@all hi", ONLINE, "Alex", 1, 2, 3),
                "@all hi", set(), set("Bob", "Carol"));
        t("case-insensitive", PingEngine.parse("@bob @HERE @EVERYONE", ONLINE, "Alex", 4, 5, 6),
                "@bob [4, 5, 6] @EVERYONE", set("Bob"), set("Carol"));
        t("boundary: no partial-name match", PingEngine.parse("@bobsmith hi", ONLINE, "Alex", 1, 2, 3),
                "@bobsmith hi", set(), set());
        t("boundary: punctuation ok", PingEngine.parse("hi (@Bob) how", ONLINE, "Alex", 1, 2, 3),
                "hi (@Bob) how", set("Bob"), set());
        t("multiple mentions dedupe", PingEngine.parse("@Bob @bob @Carol", ONLINE, "Alex", 1, 2, 3),
                "@Bob @bob @Carol", set("Bob", "Carol"), set());
        t("@everyone+@all+@name single ping", PingEngine.parse("@everyone @all @Bob", ONLINE, "Alex", 1, 2, 3),
                "@everyone @all @Bob", set("Bob"), set("Carol"));
        t("sender never pinged", PingEngine.parse("@everyone", ONLINE, "Bob", 1, 2, 3),
                "@everyone", set(), set("Alex", "Carol"));
        t("no @ sign", PingEngine.parse("hello world", ONLINE, "Alex", 1, 2, 3),
                "hello world", set(), set());
        t("null message", PingEngine.parse(null, ONLINE, "Alex", 1, 2, 3),
                "", set(), set());
        t("special token beats same-named player", PingEngine.parse("@here", Arrays.asList("here", "Alex"), "Alex", 9, 9, 9),
                "[9, 9, 9]", set(), set());
        t("negative coords", PingEngine.parse("@here", ONLINE, "Alex", -12, 64, 3),
                "[-12, 64, 3]", set(), set());
        t("two @here both replaced", PingEngine.parse("@here and @here", ONLINE, "Alex", 5, 6, 7),
                "[5, 6, 7] and [5, 6, 7]", set(), set());
        t("@everyone self-mention only sender online", PingEngine.parse("@everyone", Arrays.asList("Alex"), "Alex", 1, 2, 3),
                "@everyone", set(), set());

        if (failed > 0) {
            System.err.println("EngineTest FAILED: " + failed + " failure(s), " + passed + " passed.");
            System.exit(1);
        }
        System.out.println("EngineTest: all " + passed + " checks passed.");
    }

    private static void t(String name, PingEngine.Result result,
                          String expectedMessage, Set<String> expectedNamed, Set<String> expectedEveryone) {
        boolean ok = result.message().equals(expectedMessage)
                && result.namedTargets().equals(expectedNamed)
                && result.everyoneTargets().equals(expectedEveryone);
        if (ok) {
            passed++;
            System.out.println("  ok: " + name);
        } else {
            failed++;
            System.err.println("  FAIL: " + name
                    + "\n    message:   got '" + result.message() + "' want '" + expectedMessage + "'"
                    + "\n    named:     got " + result.namedTargets() + " want " + expectedNamed
                    + "\n    everyone:  got " + result.everyoneTargets() + " want " + expectedEveryone);
        }
    }

    private static Set<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}
