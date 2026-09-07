package dev.superseller.teleportsigns.smoke;

import dev.superseller.teleportsigns.util.Numbers;

/**
 * Offline smoke test for the money amount parser used by the sign cost
 * command. No Bukkit classes involved - runs on a plain JVM.
 */
public final class MoneyAmountTest {

    private static int failures = 0;

    public static void main(String[] args) {
        parsesPlainAndGrouped();
        parsesSuffixes();
        keepsCoordinateParserStrict();
        rejectsBadInput();

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

    private static void eq(String name, double expected, Double actual) {
        check(name, actual != null && Math.abs(expected - actual) < 1.0e-6);
    }

    private static void parsesPlainAndGrouped() {
        System.out.println("plain:");
        eq("parse 500", 500.0d, Numbers.parseMoney("500"));
        eq("parse 1,000", 1_000.0d, Numbers.parseMoney("1,000"));
        eq("parse 12.5", 12.5d, Numbers.parseMoney("12.5"));
        eq("parse zero", 0.0d, Numbers.parseMoney("0"));
    }

    private static void parsesSuffixes() {
        System.out.println("suffixes:");
        eq("parse 1k", 1_000.0d, Numbers.parseMoney("1k"));
        eq("parse 2.5K", 2_500.0d, Numbers.parseMoney("2.5K"));
        eq("parse 3m", 3_000_000.0d, Numbers.parseMoney("3m"));
        eq("parse 1.5b", 1_500_000_000.0d, Numbers.parseMoney("1.5b"));
        eq("parse 4t", 4_000_000_000_000.0d, Numbers.parseMoney("4t"));
        eq("parse 5q", 5_000_000_000_000_000.0d, Numbers.parseMoney("5q"));
        eq("parse 2.5Q", 2_500_000_000_000_000.0d, Numbers.parseMoney("2.5Q"));
    }

    private static void keepsCoordinateParserStrict() {
        System.out.println("coordinate parser stays strict:");
        check("parseDouble rejects k", Numbers.parseDouble("1k") == null);
        check("parseDouble keeps plain", Double.valueOf(42.0d).equals(Numbers.parseDouble("42")));
        check("parseInt rejects m", Numbers.parseInt("1m") == null);
    }

    private static void rejectsBadInput() {
        System.out.println("rejects:");
        check("null", Numbers.parseMoney(null) == null);
        check("empty", Numbers.parseMoney("") == null);
        check("suffix only", Numbers.parseMoney("q") == null);
        check("garbage", Numbers.parseMoney("abc") == null);
        check("unknown suffix", Numbers.parseMoney("10x") == null);
        check("negative", Numbers.parseMoney("-5") == null);
        check("infinity", Numbers.parseMoney("1e999") == null);
    }
}
