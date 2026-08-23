package dev.superseller.randomstructurechallenge.smoke;

import java.util.Map;

import dev.superseller.randomstructurechallenge.util.DurationParser;
import dev.superseller.randomstructurechallenge.util.Placeholders;
import dev.superseller.randomstructurechallenge.util.StructureKeys;
import dev.superseller.randomstructurechallenge.util.TimerBar;

/**
 * Offline smoke test for the pure (server-independent) logic.
 *
 * <pre>
 * javac -d smoke-classes \
 *   RandomStructureChallenge/smoke/SmokeTest.java \
 *   RandomStructureChallenge/src/main/java/dev/superseller/randomstructurechallenge/util/*.java \
 * java -cp smoke-classes dev.superseller.randomstructurechallenge.smoke.SmokeTest
 * </pre>
 */
public final class SmokeTest {

    private static int failures = 0;

    public static void main(String[] args) {
        testDuration();
        testTimerBar();
        testPlaceholders();
        testPrettyNames();
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

    private static void testDuration() {
        System.out.println("DurationParser:");
        check("60 seconds", DurationParser.parse("60").ok() && DurationParser.parse("60").seconds() == 60);
        check("trimmed", DurationParser.parse("  90  ").seconds() == 90);
        check("90s", DurationParser.parse("90s").seconds() == 90);
        check("1m", DurationParser.parse("1m").seconds() == 60);
        check("1m30s", DurationParser.parse("1m30s").seconds() == 90);
        check("1:30", DurationParser.parse("1:30").seconds() == 90);
        check("1:00:05", DurationParser.parse("1:00:05").seconds() == 3605);
        check("1 minute", DurationParser.parse("1 minute").seconds() == 60);
        check("2 hours", DurationParser.parse("2 hours").seconds() == 7200);
        check("30 seconds", DurationParser.parse("30 seconds").seconds() == 30);
        check("blank invalid", !DurationParser.parse("   ").ok());
        check("abc invalid", "invalid-interval".equals(DurationParser.parse("abc").errorKey()));
        check("0 too low", "interval-too-low".equals(DurationParser.parse("0").errorKey()));
        check("1:99 invalid", "invalid-interval".equals(DurationParser.parse("1:99").errorKey()));
        check("negative invalid", !DurationParser.parse("-5").ok());
    }

    private static void testTimerBar() {
        System.out.println("TimerBar:");
        check("clock 0", "00:00".equals(TimerBar.clock(0)));
        check("clock 65", "01:05".equals(TimerBar.clock(65)));
        check("clock 3600", "60:00".equals(TimerBar.clock(3600)));
        check("100s → 100%", TimerBar.percent(100, 100) == 100);
        check("99s of 100 → 99%", TimerBar.percent(99, 100) == 99);
        check("50s of 100 → 50%", TimerBar.percent(50, 100) == 50);
        check("0s → 0%", TimerBar.percent(0, 100) == 0);
        check("progress 1.0", Math.abs(TimerBar.progress(100, 100) - 1f) < 0.0001f);
        check("progress 0.5", Math.abs(TimerBar.progress(50, 100) - 0.5f) < 0.0001f);
        check("bar width 10 full", "▰▰▰▰▰▰▰▰▰▰".equals(TimerBar.bar(100, 100, 10)));
        check("bar width 10 empty", "▱▱▱▱▱▱▱▱▱▱".equals(TimerBar.bar(0, 100, 10)));
        check("bar half", TimerBar.filledBlocks(50, 100, 10) == 5);
        check("interval 0 percent", TimerBar.percent(10, 0) == 0);
    }

    private static void testPlaceholders() {
        System.out.println("Placeholders:");
        Map<String, String> map = Map.of("seconds", "42", "structure", "Igloo");
        check("replace seconds",
                "seconds left until next structure: 42"
                        .equals(Placeholders.apply("seconds left until next structure: {seconds}", map)));
        check("replace structure",
                "drop Igloo".equals(Placeholders.apply("drop {structure}", map)));
        check("missing stays", "{nope}".equals(Placeholders.apply("{nope}", map)));
        check("null template", "".equals(Placeholders.apply(null, map)));
    }

    private static void testPrettyNames() {
        System.out.println("Pretty names:");
        check("village plains", "Village Plains".equals(TimerBar.prettyStructure("minecraft:village_plains")));
        check("bare key", "Igloo".equals(TimerBar.prettyStructure("igloo")));
        check("empty", "unknown".equals(TimerBar.prettyStructure("")));
        check("normalize adds namespace",
                "minecraft:pillager_outpost".equals(StructureKeys.normalize("pillager_outpost")));
        check("normalize keeps namespace",
                "minecraft:igloo".equals(StructureKeys.normalize("minecraft:igloo")));
        check("safe key", StructureKeys.safe("minecraft:village_plains"));
        check("rejects injection", !StructureKeys.safe("minecraft:village; op"));
    }
}
