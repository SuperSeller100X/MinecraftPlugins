package dev.superseller.playerbank.smoke;

import dev.superseller.playerbank.gui.Amount;
import dev.superseller.playerbank.gui.AmountParser;

/**
 * Offline unit checks for the GUI amount parser (pure logic, no Bukkit).
 * Run by build.sh / CI — exit code 1 on any failure.
 */
public final class AmountParserTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        // --- plain numbers and grouping ---
        amount("250", Amount.Type.EXACT, 250d);
        amount("2.50", Amount.Type.EXACT, 2.5d);
        amount("1,000", Amount.Type.EXACT, 1000d);
        amount("1,000,000.50", Amount.Type.EXACT, 1000000.5d);
        amount(" 1000 ", Amount.Type.EXACT, 1000d);

        // --- keywords ---
        amount("all", Amount.Type.ALL, 0d);
        amount("MAX", Amount.Type.ALL, 0d);
        amount("half", Amount.Type.HALF, 0d);
        amount("50%", Amount.Type.HALF, 0d);

        // --- suffixes ---
        amount("1k", Amount.Type.EXACT, 1000d);
        amount("2.5K", Amount.Type.EXACT, 2500d);
        amount("1m", Amount.Type.EXACT, 1000000d);
        amount("10M", Amount.Type.EXACT, 10000000d);
        amount("1b", Amount.Type.EXACT, 1000000000d);
        amount("1t", Amount.Type.EXACT, 1000000000000d);
        amount("1,5k".replace(',', '.'), Amount.Type.EXACT, 1500d);

        // --- percentages ---
        amount("25%", Amount.Type.PERCENT, 25d);
        amount("12.5%", Amount.Type.PERCENT, 12.5d);
        amount("100%", Amount.Type.PERCENT, 100d);

        // --- invalid ---
        invalid(null);
        invalid("");
        invalid("   ");
        invalid("abc");
        invalid("-5");
        invalid("0");
        invalid("0%");
        invalid("101%");
        invalid("nan");
        invalid("1x");
        invalid("infinity");
        invalid("1 000");
        invalid("k");

        // --- resolve ---
        resolve("all", 123.45d, 123.45d);
        resolve("half", 100d, 50d);
        resolve("half", 10.01d, 5.005d);
        resolve("25%", 200d, 50d);
        resolve("10", 9999d, 10d);
        resolve("all", 0d, 0d);

        // --- labels ---
        label("all", "All");
        label("half", "Half");
        label("25%", "25%");
        label("1000", "1,000");
        label("2.50", "2.5");
        label("1000000", "1,000,000");
        label("0.05", "0.05");
        label("1234567.89", "1,234,567.89");

        // --- dialog key tokens round-trip ---
        token("all", "all");
        token("half", "half");
        token("25%", "25p");
        token("1000", "1000");
        token("2.5k", "2500");
        token("1m", "1000000");
        token("0.00001", "0.00001"); // never E-notation: that would break dialog keys
        roundTrip("all");
        roundTrip("half");
        roundTrip("25%");
        roundTrip("1000");
        roundTrip("2.5k");
        roundTrip("1m");
        roundTrip("7.5%");
        roundTrip("0.00001");

        report();
    }

    private static void amount(String raw, Amount.Type type, double value) {
        Amount a = AmountParser.parse(raw);
        if (a == null) {
            fail("parse('" + raw + "') returned null, expected " + type);
            return;
        }
        if (a.type() != type) {
            fail("parse('" + raw + "') type " + a.type() + " != " + type);
            return;
        }
        if (type == Amount.Type.EXACT || type == Amount.Type.PERCENT) {
            if (Math.abs(a.value() + a.percent() - value) > 1e-9
                    && Math.abs((type == Amount.Type.EXACT ? a.value() : a.percent()) - value) > 1e-9) {
                fail("parse('" + raw + "') value mismatch: " + a.value() + "/" + a.percent()
                        + " != " + value);
                return;
            }
        }
        pass("parse('" + raw + "')");
    }

    private static void invalid(String raw) {
        if (AmountParser.parse(raw) == null) {
            pass("parse(" + (raw == null ? "null" : "'" + raw + "'") + ") rejected");
        } else {
            fail("parse(" + (raw == null ? "null" : "'" + raw + "'") + ") should be invalid");
        }
    }

    private static void resolve(String raw, double available, double expected) {
        Amount a = AmountParser.parse(raw);
        if (a == null) {
            fail("resolve('" + raw + "'): parse failed");
            return;
        }
        double got = a.resolve(available);
        if (Math.abs(got - expected) > 1e-9) {
            fail("resolve('" + raw + "', " + available + ") = " + got + " != " + expected);
        } else {
            pass("resolve('" + raw + "', " + available + ")");
        }
    }

    private static void label(String raw, String expected) {
        Amount a = AmountParser.parse(raw);
        if (a == null) {
            fail("label('" + raw + "'): parse failed");
            return;
        }
        if (!expected.equals(a.label())) {
            fail("label('" + raw + "') = '" + a.label() + "' != '" + expected + "'");
        } else {
            pass("label('" + raw + "')");
        }
    }

    private static void token(String raw, String expected) {
        Amount a = AmountParser.parse(raw);
        if (a == null) {
            fail("token('" + raw + "'): parse failed");
            return;
        }
        if (!expected.equals(a.token())) {
            fail("token('" + raw + "') = '" + a.token() + "' != '" + expected + "'");
        } else {
            pass("token('" + raw + "')");
        }
    }

    private static void roundTrip(String raw) {
        Amount a = AmountParser.parse(raw);
        if (a == null) {
            fail("roundTrip('" + raw + "'): parse failed");
            return;
        }
        Amount back = AmountParser.fromToken(a.token());
        if (back == null) {
            fail("roundTrip('" + raw + "'): token '" + a.token() + "' did not decode");
            return;
        }
        if (back.type() != a.type()) {
            fail("roundTrip('" + raw + "'): type " + back.type() + " != " + a.type());
            return;
        }
        if (Math.abs(back.resolve(1000d) - a.resolve(1000d)) > 1e-9) {
            fail("roundTrip('" + raw + "'): resolve mismatch after token decode");
        } else {
            pass("roundTrip('" + raw + "')");
        }
    }

    private static void pass(String name) {
        passed++;
    }

    private static void fail(String message) {
        failed++;
        System.out.println("FAIL: " + message);
    }

    private static void report() {
        System.out.println("AmountParserTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
