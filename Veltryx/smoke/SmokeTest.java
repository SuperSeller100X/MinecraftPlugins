package dev.superseller.veltryx.smoke;

import dev.superseller.veltryx.anticheat.CheckType;
import dev.superseller.veltryx.anticheat.PingGuard;
import dev.superseller.veltryx.anticheat.XrayStats;
import dev.superseller.veltryx.util.Money;
import dev.superseller.veltryx.util.TimeUtil;

/**
 * Offline smoke tests for the pure logic (no Bukkit involved).
 * Run by tools/run_smoke.sh — exit code 1 on any failure.
 */
public final class SmokeTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        // Money
        t("money plain", Money.parse("1000"), 100_000L);
        t("money decimal", Money.parse("1.5"), 150L);
        t("money k suffix", Money.parse("1.5k"), 150_000L);
        t("money symbol", Money.parse("$1,000"), 100_000L);
        t("money invalid", Money.parse("abc"), Money.INVALID);
        t("money negative", Money.parse("-5"), Money.INVALID);
        t("money format", Money.format(125_050L), "$1,250.50");
        t("money short", Money.formatShort(125_000L), "1,250");
        t("money saturate", Money.addSaturated(Long.MAX_VALUE, 5L), Long.MAX_VALUE);

        // TimeUtil
        t("time plain", TimeUtil.parseSeconds("30"), 30L);
        t("time m", TimeUtil.parseSeconds("5m"), 300L);
        t("time d", TimeUtil.parseSeconds("30d"), 2_592_000L);
        t("time w", TimeUtil.parseSeconds("1w"), 604_800L);
        t("time 2w", TimeUtil.parseSeconds("2w"), 1_209_600L);
        t("time perm", TimeUtil.parseSeconds("perm"), Long.MAX_VALUE);
        t("time invalid", TimeUtil.parseSeconds("1.5h"), TimeUtil.INVALID);
        t("time zero", TimeUtil.parseSeconds("0"), TimeUtil.INVALID);
        t("time format", TimeUtil.format(330L), "5m 30s");
        t("time format perm", TimeUtil.format(Long.MAX_VALUE), "perm");
        t("time expiry perm", TimeUtil.expiryMillis(1000L, Long.MAX_VALUE), TimeUtil.NEVER);

        // PingGuard
        t("guard base", close(PingGuard.allowanceFactor(50, 20.0), 1.0), true);
        t("guard ping grows", PingGuard.allowanceFactor(150, 20.0) > 1.1, true);
        t("guard capped", close(PingGuard.allowanceFactor(9999, 20.0), 2.2), true);
        t("guard combined cap", PingGuard.allowanceFactor(9999, 1.0) <= 3.0, true);
        t("guard skip ping", PingGuard.shouldSkip(400, 20.0, 350, 18.0), true);
        t("guard skip tps", PingGuard.shouldSkip(100, 15.0, 350, 18.0), true);
        t("guard ok", PingGuard.shouldSkip(100, 20.0, 350, 18.0), false);
        t("guard reliable", PingGuard.reliable(100, 20.0, 250, 19.0), true);
        t("guard unreliable ping", PingGuard.reliable(300, 20.0, 250, 19.0), false);

        // XrayStats
        t("xray hidden 0", close(XrayStats.hiddenness(0), 1.0), true);
        t("xray hidden 1", close(XrayStats.hiddenness(1), 0.7), true);
        t("xray hidden 2", close(XrayStats.hiddenness(2), 0.3), true);
        t("xray hidden 3", close(XrayStats.hiddenness(3), 0.0), true);
        t("xray avg", close(XrayStats.averageHiddenness(3.0, 5), 0.6), true);
        t("xray suspicious", XrayStats.suspicious(5.0, 6, 0.55, 6), true);
        t("xray not enough", XrayStats.suspicious(5.0, 5, 0.55, 6), false);
        t("xray vl capped", XrayStats.violationLevel(200.0, 200, 0.55, 6) <= 10.0, true);

        // CheckType
        t("check fly path", CheckType.FLY.configPath(), "movement.fly");
        t("check reach path", CheckType.INTERACT_REACH.configPath(), "interact.interact-reach");
        t("check xray path", CheckType.XRAY.configPath(), "xray.xray");

        System.out.println("Smoke: " + passed + " passed, " + failed + " failed.");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) < 0.0001;
    }

    private static void t(String name, Object actual, Object expected) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL " + name + ": expected " + expected + " but got " + actual);
        }
    }
}
