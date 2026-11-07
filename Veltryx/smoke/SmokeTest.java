package dev.superseller.veltryx.smoke;

import dev.superseller.veltryx.anticheat.CheckType;
import dev.superseller.veltryx.anticheat.PingGuard;
import dev.superseller.veltryx.anticheat.XrayStats;
import dev.superseller.veltryx.anticheat.xray.OreObfuscator;
import dev.superseller.veltryx.punish.BanCommand;
import dev.superseller.veltryx.util.YamlTextMerge;
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
        t("money m suffix", Money.parse("2m"), 200_000_000L);
        t("money b suffix", Money.parse("1b"), 100_000_000_000L);
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

        // /ban argument parsing (BanCommand.parse): duration + free-text reason
        t("ban parse temp", BanCommand.parse(new String[]{"steve", "7d", "cheating"}).seconds(), 604_800L);
        t("ban parse temp reason", BanCommand.parse(new String[]{"steve", "7d", "cheating"}).reason(), "cheating");
        t("ban parse perm", BanCommand.parse(new String[]{"steve", "perm", "griefing"}).seconds(), Long.MAX_VALUE);
        t("ban parse no-duration", BanCommand.parse(new String[]{"steve", "cheating", "at", "pvp"}).seconds(), Long.MAX_VALUE);
        t("ban parse no-duration reason", BanCommand.parse(new String[]{"steve", "cheating", "at", "pvp"}).reason(), "cheating at pvp");
        t("ban parse invalid-duration-is-reason", BanCommand.parse(new String[]{"steve", "xray", "ban"}).reason(), "xray ban");
        t("ban parse duration-without-reason", BanCommand.parse(new String[]{"steve", "7d"}), null);
        t("ban parse missing-args", BanCommand.parse(new String[]{"steve"}), null);

        // Anti-xray helpers (OreObfuscator)
        t("xray is ore", OreObfuscator.isOre(org.bukkit.Material.EMERALD_ORE), Boolean.TRUE);
        t("xray not ore", OreObfuscator.isOre(org.bukkit.Material.STONE), Boolean.FALSE);
        t("xray fake deepslate", OreObfuscator.fakeFor(org.bukkit.Material.DEEPSLATE_DIAMOND_ORE),
                org.bukkit.Material.DEEPSLATE);
        t("xray fake nether", OreObfuscator.fakeFor(org.bukkit.Material.ANCIENT_DEBRIS),
                org.bukkit.Material.NETHERRACK);
        t("xray fake stone", OreObfuscator.fakeFor(org.bukkit.Material.DIAMOND_ORE),
                org.bukkit.Material.STONE);
        t("xray pack distinct y", Boolean.valueOf(OreObfuscator.packBlock(5, 10, -3) != OreObfuscator.packBlock(5, 11, -3)), Boolean.TRUE);
        t("xray pack distinct x", Boolean.valueOf(OreObfuscator.packBlock(-30_000_000, -64, 12) != OreObfuscator.packBlock(-29_999_999, -64, 12)), Boolean.TRUE);
        t("xray pack negative ok", Long.valueOf(OreObfuscator.packBlock(-1, -1, -1)), Long.valueOf(OreObfuscator.packBlock(-1, -1, -1)));
        t("xray chunk pack distinct", Boolean.valueOf(OreObfuscator.packChunk(-5, 7) != OreObfuscator.packChunk(5, 7)), Boolean.TRUE);

        // YamlTextMerge: config auto-update keeps user values, adds new keys
        String template = "anticheat:\n"
                + "  xray:\n"
                + "    obfuscation: true\n"
                + "    # reveal comment\n"
                + "    reveal-radius: 5\n"
                + "    hide-exposed: true\n"
                + "    scan-y-min: -64\n"
                + "moderation:\n"
                + "  warn-expiry: 30d\n";
        String userCfg = "anticheat:\n"
                + "  xray:\n"
                + "    obfuscation: false\n"
                + "    radius: 4\n"
                + "    scan-y-min: -64\n";
        String merged = YamlTextMerge.merge(template, userCfg);
        t("merge keeps user value", merged.contains("obfuscation: false"), Boolean.TRUE);
        t("merge keeps old key", merged.contains("radius: 4"), Boolean.TRUE);
        t("merge adds reveal-radius", merged.contains("reveal-radius: 5"), Boolean.TRUE);
        t("merge adds hide-exposed", merged.contains("hide-exposed: true"), Boolean.TRUE);
        t("merge adds comment", merged.contains("# reveal comment"), Boolean.TRUE);
        t("merge adds missing section", merged.contains("warn-expiry: 30d"), Boolean.TRUE);
        t("merge inserts in section", merged.indexOf("reveal-radius") > merged.indexOf("xray:")
                && merged.indexOf("reveal-radius") < merged.indexOf("moderation:"), Boolean.TRUE);
        t("merge idempotent", YamlTextMerge.merge(template, merged).equals(merged), Boolean.TRUE);
        t("merge no-op when complete", YamlTextMerge.merge(template, template).equals(template), Boolean.TRUE);
        String flatUser = "general.no-permission: \"<red>No.</red>\"\n";
        String flatTemplate = "general.no-permission: \"<red>No.</red>\"\nbans.none: \"<green>None</green>\"\n";
        String flatMerged = YamlTextMerge.merge(flatTemplate, flatUser);
        t("merge flat key appended", flatMerged.contains("bans.none"), Boolean.TRUE);
        t("merge flat keeps value", flatMerged.contains("<red>No.</red>"), Boolean.TRUE);

        // SpeedGuard: the tolerance must cover the fastest legitimate vanilla
        // movement (sprint-jump chains) and never false-flag it.
        {
            var sgFlat = dev.superseller.veltryx.anticheat.SpeedGuard.situationalMultiplier(-1, false, false, false, false);
            t("sg flat multiplier", sgFlat == 1.0, Boolean.TRUE);
            var sgSpeed2 = dev.superseller.veltryx.anticheat.SpeedGuard.situationalMultiplier(1, false, false, false, false);
            t("sg speed-2 multiplier", Math.abs(sgSpeed2 - 1.4) < 1e-9, Boolean.TRUE);
            var sgJump = dev.superseller.veltryx.anticheat.SpeedGuard.situationalMultiplier(-1, false, false, false, true);
            t("sg jump-chain multiplier", Math.abs(sgJump - 1.42) < 1e-9, Boolean.TRUE);
            var sgIce = dev.superseller.veltryx.anticheat.SpeedGuard.situationalMultiplier(-1, true, false, false, false);
            t("sg ice multiplier", Math.abs(sgIce - 1.6) < 1e-9, Boolean.TRUE);
            var sgSoul = dev.superseller.veltryx.anticheat.SpeedGuard.situationalMultiplier(-1, false, false, true, false);
            t("sg soul-speed multiplier", Math.abs(sgSoul - 1.45) < 1e-9, Boolean.TRUE);
            var sgAll = dev.superseller.veltryx.anticheat.SpeedGuard.situationalMultiplier(4, true, true, true, true);
            t("sg multiplier capped", sgAll == 4.5, Boolean.TRUE);
            var sgLimit = dev.superseller.veltryx.anticheat.SpeedGuard.limit(6.5, 1.0, sgJump);
            t("sg limit math", Math.abs(sgLimit - 9.23) < 1e-9, Boolean.TRUE);
            // THE false-flag regression: flat sprint-jump (~8 b/s) with no effects
            t("sg sprint-jump never flags",
                    dev.superseller.veltryx.anticheat.SpeedGuard.verdict(8.0, sgLimit, 1.75, 0)
                            == dev.superseller.veltryx.anticheat.SpeedGuard.OK, Boolean.TRUE);
            // sprint-jump + speed II (~11 b/s)
            var sgLimit2 = dev.superseller.veltryx.anticheat.SpeedGuard.limit(6.5, 1.0,
                    dev.superseller.veltryx.anticheat.SpeedGuard.situationalMultiplier(1, false, false, false, true));
            t("sg speed-2 sprint-jump never flags",
                    dev.superseller.veltryx.anticheat.SpeedGuard.verdict(11.0, sgLimit2, 1.75, 0)
                            == dev.superseller.veltryx.anticheat.SpeedGuard.OK, Boolean.TRUE);
            // jitter under 15% is silent; mild excess needs a streak of 3
            t("sg jitter ok",
                    dev.superseller.veltryx.anticheat.SpeedGuard.verdict(7.2, 6.5, 1.75, 0)
                            == dev.superseller.veltryx.anticheat.SpeedGuard.OK, Boolean.TRUE);
            t("sg suspect silent",
                    dev.superseller.veltryx.anticheat.SpeedGuard.verdict(7.6, 6.5, 1.75, 0)
                            == dev.superseller.veltryx.anticheat.SpeedGuard.SUSPECT, Boolean.TRUE);
            t("sg streak flags",
                    dev.superseller.veltryx.anticheat.SpeedGuard.verdict(7.6, 6.5, 1.75, 2)
                            == dev.superseller.veltryx.anticheat.SpeedGuard.FLAG, Boolean.TRUE);
            // impossible movement rolls back immediately, streak or not
            t("sg impossible rolls back",
                    dev.superseller.veltryx.anticheat.SpeedGuard.verdict(12.0, 6.5, 1.75, 0)
                            == dev.superseller.veltryx.anticheat.SpeedGuard.ROLLBACK, Boolean.TRUE);
            t("sg rollback beats streak",
                    dev.superseller.veltryx.anticheat.SpeedGuard.verdict(12.0, 6.5, 1.75, 5)
                            == dev.superseller.veltryx.anticheat.SpeedGuard.ROLLBACK, Boolean.TRUE);
            t("sg under hard cap is not rollback",
                    dev.superseller.veltryx.anticheat.SpeedGuard.verdict(11.3, 6.5, 1.75, 2)
                            == dev.superseller.veltryx.anticheat.SpeedGuard.FLAG, Boolean.TRUE);
        }

        // Real templates: self-merge is a no-op and upgrades stay idempotent
        try {
            String cfg = new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Path.of("src/main/resources/config.yml")));
            String msg = new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Path.of("src/main/resources/messages.yml")));
            t("merge real config self", YamlTextMerge.merge(cfg, cfg).equals(cfg), Boolean.TRUE);
            t("merge real messages self", YamlTextMerge.merge(msg, msg).equals(msg), Boolean.TRUE);
            String oldUser = "anticheat:\n  xray:\n    obfuscation: true\n    radius: 4\n    scan-y-min: -64\n";
            String upgraded = YamlTextMerge.merge(cfg, oldUser);
            t("merge real keeps user", upgraded.contains("radius: 4"), Boolean.TRUE);
            t("merge real adds keys", upgraded.contains("hide-exposed") && upgraded.contains("window: 20"), Boolean.TRUE);
            t("merge real idempotent", YamlTextMerge.merge(cfg, upgraded).equals(upgraded), Boolean.TRUE);
            t("merge real messages upgrade", YamlTextMerge.merge(msg, "prefix: \"P \"\n").contains("ban.usage"), Boolean.TRUE);
        } catch (java.io.IOException e) {
            t("merge real files readable", Boolean.FALSE, Boolean.TRUE);
        }
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
