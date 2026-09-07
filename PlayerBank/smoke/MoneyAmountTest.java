package dev.superseller.playerbank.smoke;

import dev.superseller.playerbank.util.MoneyAmount;

/**
 * Offline smoke test for the money amount parser used by the bank commands.
 * No Bukkit classes involved - runs on a plain JVM.
 */
public final class MoneyAmountTest {

    private static int failures = 0;

    public static void main(String[] args) {
        parsesPlainAndGrouped();
        parsesSuffixes();
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
        eq("parse 500", 500.0d, MoneyAmount.parse("500"));
        eq("parse 1,000", 1_000.0d, MoneyAmount.parse("1,000"));
        eq("parse 1 000 000", 1_000_000.0d, MoneyAmount.parse("1 000 000"));
        eq("parse 12.5", 12.5d, MoneyAmount.parse("12.5"));
    }

    private static void parsesSuffixes() {
        System.out.println("suffixes:");
        eq("parse 1k", 1_000.0d, MoneyAmount.parse("1k"));
        eq("parse 2.5K", 2_500.0d, MoneyAmount.parse("2.5K"));
        eq("parse 3m", 3_000_000.0d, MoneyAmount.parse("3m"));
        eq("parse 1.5b", 1_500_000_000.0d, MoneyAmount.parse("1.5b"));
        eq("parse 4t", 4_000_000_000_000.0d, MoneyAmount.parse("4t"));
        eq("parse 5q", 5_000_000_000_000_000.0d, MoneyAmount.parse("5q"));
        eq("parse 2.5Q", 2_500_000_000_000_000.0d, MoneyAmount.parse("2.5Q"));
    }

    private static void rejectsBadInput() {
        System.out.println("rejects:");
        check("null", MoneyAmount.parse(null) == null);
        check("empty", MoneyAmount.parse("") == null);
        check("suffix only", MoneyAmount.parse("k") == null);
        check("garbage", MoneyAmount.parse("abc") == null);
        check("unknown suffix", MoneyAmount.parse("10x") == null);
        check("negative", MoneyAmount.parse("-5") == null);
        check("negative suffix", MoneyAmount.parse("-1k") == null);
        check("infinity", MoneyAmount.parse("1e999") == null);
    }
}
