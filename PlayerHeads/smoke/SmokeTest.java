package dev.superseller.playerheads.smoke;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.superseller.playerheads.util.Amounts;
import dev.superseller.playerheads.util.Names;
import dev.superseller.playerheads.util.Placeholders;

/**
 * Tiny offline smoke test that exercises the pure (server-independent) parts
 * of PlayerHeads: name validation, amount parsing and placeholder
 * substitution. Run from the repo root with:
 *
 * <pre>
 * javac -d smoke-classes PlayerHeads/smoke/SmokeTest.java \
 *       PlayerHeads/src/main/java/dev/superseller/playerheads/util/*.java
 * java -cp smoke-classes dev.superseller.playerheads.smoke.SmokeTest
 * </pre>
 */
public final class SmokeTest {

    private static int failures = 0;

    public static void main(String[] args) {
        testNames();
        testAmounts();
        testPlaceholders();
        if (failures > 0) {
            System.out.println("SMOKE TEST FAILED: " + failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("SMOKE TEST OK");
    }

    private static void check(String name, boolean condition) {
        System.out.println((condition ? "  ok  " : "  FAIL ") + name);
        if (!condition) {
            failures++;
        }
    }

    private static void testNames() {
        System.out.println("Names:");
        var pattern = Names.DEFAULT_PATTERN;
        check("accepts 'Notch'", Names.isValid("Notch", pattern));
        check("accepts 16 chars", Names.isValid("abcdefghijklmnop", pattern));
        check("accepts digits/underscore", Names.isValid("Player_123", pattern));
        check("rejects null", !Names.isValid(null, pattern));
        check("rejects empty", !Names.isValid("", pattern));
        check("rejects 17 chars", !Names.isValid("abcdefghijklmnopq", pattern));
        check("rejects spaces", !Names.isValid("Hello World", pattern));
        check("rejects color codes", !Names.isValid("&cNotch", pattern));
        check("rejects dash", !Names.isValid("Notch-1", pattern));
    }

    private static void testAmounts() {
        System.out.println("Amounts:");
        check("null -> default", Amounts.parse(null, 3, 64).ok() && Amounts.parse(null, 3, 64).amount() == 3);
        check("blank -> default", Amounts.parse("   ", 2, 64).amount() == 2);
        check("'5' -> 5", Amounts.parse("5", 1, 64).amount() == 5);
        check("whitespace trimmed", Amounts.parse(" 7 ", 1, 64).amount() == 7);
        check("default above max clamps to max", Amounts.parse(null, 99, 64).amount() == 64);
        check("'0' -> amount-too-low", "amount-too-low".equals(Amounts.parse("0", 1, 64).errorKey()));
        check("'-3' -> amount-too-low", "amount-too-low".equals(Amounts.parse("-3", 1, 64).errorKey()));
        check("'65' -> amount-too-high", "amount-too-high".equals(Amounts.parse("65", 1, 64).errorKey()));
        check("huge number -> amount-too-high", "amount-too-high".equals(
                Amounts.parse("99999999999999999999", 1, 64).errorKey()));
        check("overflow-safe at long range", "amount-too-high".equals(
                Amounts.parse("9223372036854775808", 1, 64).errorKey()));
        check("'abc' -> invalid-amount", "invalid-amount".equals(Amounts.parse("abc", 1, 64).errorKey()));
        check("'1.5' -> invalid-amount", "invalid-amount".equals(Amounts.parse("1.5", 1, 64).errorKey()));
        check("max placeholder filled", "64".equals(
                Amounts.parse("65", 1, 64).placeholders().get("max")));
        check("input placeholder filled", "1.5".equals(
                Amounts.parse("1.5", 1, 64).placeholders().get("input")));
        check("bypass limit 65536 accepted", Amounts.parse("65536", 1, 65536).amount() == 65536);
    }

    private static void testPlaceholders() {
        System.out.println("Placeholders:");
        Map<String, String> map = new LinkedHashMap<>();
        map.put("player", "Notch");
        map.put("amount", "5");
        check("simple substitution",
                "You received 5x the head of Notch.".equals(
                        Placeholders.apply("You received {amount}x the head of {player}.", map)));
        check("repeated placeholders",
                "Notch and Notch".equals(Placeholders.apply("{player} and {player}", map)));
        check("missing placeholder untouched",
                "{max} stays".equals(Placeholders.apply("{max} stays", map)));
        check("empty map is a no-op",
                "hello {x}".equals(Placeholders.apply("hello {x}", Map.of())));
    }
}
