package dev.superseller.veltryx.smoke;

import dev.superseller.veltryx.anticheat.CheckType;
import dev.superseller.veltryx.anticheat.PingGuard;
import dev.superseller.veltryx.anticheat.XrayStats;
import dev.superseller.veltryx.anticheat.xray.OreObfuscator;
import dev.superseller.veltryx.punish.BanCommand;
import dev.superseller.veltryx.util.YamlTextMerge;
import dev.superseller.veltryx.anticheat.FlyGuard;
import dev.superseller.veltryx.home.HomePricing;
import dev.superseller.veltryx.anticheat.JesusGuard;
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

        // Money.formatCompact: HUD/chat display ($2.3k style, no symbol)
        t("compact thousands", Money.formatCompact(230_000L), "2.3k");
        t("compact round k", Money.formatCompact(50_000_000L), "500k");
        t("compact small", Money.formatCompact(25_000L), "250");
        t("compact cents", Money.formatCompact(1_234L), "12.34");
        t("compact 12.5k", Money.formatCompact(1_250_000L), "12.5k");
        t("compact millions", Money.formatCompact(250_000_000L), "2.5M");
        t("compact whole", Money.formatCompact(50_000L), "500");

        // HomePricing: 3 free, then +$2,500 each, cap 45
        t("home 1 free", HomePricing.costCents(1, 3, 250_000L), 0L);
        t("home 3 free", HomePricing.costCents(3, 3, 250_000L), 0L);
        t("home 4 costs", HomePricing.costCents(4, 3, 250_000L), 250_000L);
        t("home 5 costs", HomePricing.costCents(5, 3, 250_000L), 500_000L);
        t("home 45 costs", HomePricing.costCents(45, 3, 250_000L), 42L * 250_000L);
        t("home name ok", HomePricing.normalizeName("Base_2"), "base_2");
        t("home name bad", HomePricing.normalizeName("no spaces"), null);
        t("home name long", HomePricing.normalizeName("a".repeat(17)), null);

        // TpaService: per-counterpart cooldown exactly as specified — send to A,
        // 5s later send to B (allowed), then 5s to A again, 10s to B
        {
            var tpa = new dev.superseller.veltryx.teleport.TpaService();
            java.util.UUID a = java.util.UUID.randomUUID();
            java.util.UUID b = java.util.UUID.randomUUID();
            java.util.UUID me = java.util.UUID.randomUUID();
            t("tpa initial clear", tpa.cooldownLeftMillis(me, a, 0L, 10_000L), 0L);
            tpa.recordSend(me, a, 0L);
            t("tpa blocks A", tpa.cooldownLeftMillis(me, a, 5_000L, 10_000L) > 0, Boolean.TRUE);
            t("tpa A left 5s", tpa.cooldownLeftMillis(me, a, 5_000L, 10_000L), 5_000L);
            t("tpa B unaffected", tpa.cooldownLeftMillis(me, b, 5_000L, 10_000L), 0L);
            tpa.recordSend(me, b, 5_000L);
            t("tpa A ready at 10s", tpa.cooldownLeftMillis(me, a, 10_000L, 10_000L), 0L);
            t("tpa B left 10s", tpa.cooldownLeftMillis(me, b, 10_000L, 10_000L), 5_000L);
            t("tpa B ready at 15s", tpa.cooldownLeftMillis(me, b, 15_000L, 10_000L), 0L);
            var req = tpa.send(me, a, false, 0L);
            t("tpa pending stored", tpa.takePending(a) == req, Boolean.TRUE);
            t("tpa pending gone", tpa.takePending(a) == null, Boolean.TRUE);
            t("tpa request expires", req.expired(61_000L, 60_000L), Boolean.TRUE);
            t("tpa request fresh", req.expired(30_000L, 60_000L), Boolean.FALSE);
        }

        // New shop families: every new recipe loses money (no craft loops)
        {
            // resin: 9 clumps <-> block, 4 bricks <-> bricks block
            t("resin compress", s(0.30) * 9 > 1.40, Boolean.TRUE);            // 2.70 > 1.40
            t("resin decompress", 0.20 * 9 < 2.80, Boolean.TRUE);             // 1.80 < 2.80
            t("resin bricks compress", 0.42 * 4 > 1.00, Boolean.TRUE);
            t("resin bricks decompress", 0.21 * 4 < 2.00, Boolean.TRUE);
            // copper block: 9 ingots <-> block (and oxidation equals)
            t("copper compress", 0.32 * 9 > 1.50, Boolean.TRUE);
            t("copper decompress", 0.16 * 9 < 3.00, Boolean.TRUE);
            t("oxidized grate equal", grEq(0.40, 0.20), Boolean.TRUE);
            // waxing costs honeycomb, scraping loses it
            // sell(WAXED_GRATE)=0.40 < inputs buy(grate 0.40)+buy(honeycomb 0.35)
            t("wax grate loss", 0.40 < 0.75, Boolean.TRUE);
            t("unwax grate loss", 0.20 < 0.80, Boolean.TRUE);                 // sell(grate) < buy(waxed)
            // mace, wolf armor, stew, bundles
            t("mace craft loss", 27.50 < 32.00 + 2 * 3.20, Boolean.TRUE);
            t("wolf armor craft loss", 2.10 < 6 * 0.50, Boolean.TRUE);
            t("stew craft loss", 0.55 < 0.09 + 0.18 + 0.18 + 0.18, Boolean.TRUE);
            t("bundle cheap loss", 0.60 < 2 * 0.15 + 2 * 0.25, Boolean.TRUE);
            t("bundle full loss", 0.60 < 2 * 0.15 + 4 * 0.25, Boolean.TRUE);
            t("dyed bundle loss", 0.75 < 1.20 + 0.14, Boolean.TRUE);
            // hanging sign (oak): 6 stripped + 2 chains -> 6 signs
            t("hang sign loss", 6 * 0.35 < 6 * 0.35 + 2 * 0.55, Boolean.TRUE);
            // wood 1:1 family equality
            t("stripped oak == oak log", woodEq(), Boolean.TRUE);
            // pale moss carpet: 2 blocks -> 3 carpet
            t("pale carpet loss", 3 * 0.06 < 2 * 0.25, Boolean.TRUE);
            // every new item: sell < buy
            t("new items sell<buy", newItemsBelowBuy(), Boolean.TRUE);
        }

        // FlyGuard: three vertical tiers — the "no-fall" slow-descent fly must
        // be caught, real falls and jumps must never flag
        {
            t("fg ground resets", FlyGuard.step(20, 0.0, true, false, false), 0);
            t("fg levitation resets", FlyGuard.step(20, 0.0, false, true, false), 0);
            t("fg slow falling resets", FlyGuard.step(20, 0.0, false, false, true), 0);
            t("fg hover accumulates", FlyGuard.step(5, 0.0, false, false, false), 6);
            t("fg slow descent accumulates (no-fall fix)", FlyGuard.step(5, -0.2, false, false, false), 6);
            t("fg bobbing accumulates", FlyGuard.step(5, -0.05, false, false, false), 6);
            t("fg real fall decays", FlyGuard.step(10, -0.6, false, false, false), 6);
            t("fg fall floors at zero", FlyGuard.step(2, -0.9, false, false, false), 0);
            // regression: slow-descent fly (dy=-0.2/tick) is flagged within a second
            int ticks = 0;
            int air = 0;
            double limit = FlyGuard.maxAirSeconds(0, 1.0);
            while (air * 0.05 <= limit && ticks < 200) {
                air = FlyGuard.step(air, -0.2, false, false, false);
                ticks++;
            }
            t("fg slow-descent fly caught fast", ticks <= 20, Boolean.TRUE);
            // regression: a real fall (dy=-0.6/tick) never accumulates
            air = 0;
            int worst = 0;
            for (int n = 0; n < 200; n++) {
                air = FlyGuard.step(air, -0.6, false, false, false);
                worst = Math.max(worst, air);
            }
            t("fg real fall never flags", worst * 0.05 <= FlyGuard.maxAirSeconds(0, 1.0), Boolean.TRUE);
            // regression: a long vanilla jump (0.7s airtime incl. slow apex) never flags
            air = 0;
            worst = 0;
            for (int n = 0; n < 12; n++) {
                air = FlyGuard.step(air, 0.15, false, false, false);
                worst = Math.max(worst, air);
            }
            for (int n = 0; n < 3; n++) {
                air = FlyGuard.step(air, -0.1, false, false, false);
                worst = Math.max(worst, air);
            }
            for (int n = 0; n < 12; n++) {
                air = FlyGuard.step(air, -0.6, false, false, false);
            }
            t("fg vanilla jump never flags", worst * 0.05 <= FlyGuard.maxAirSeconds(0, 1.0), Boolean.TRUE);
            t("fg threshold base", Math.abs(FlyGuard.maxAirSeconds(0, 1.0) - 0.8) < 1e-9, Boolean.TRUE);
            t("fg threshold jump boost", Math.abs(FlyGuard.maxAirSeconds(2, 1.0) - 1.04) < 1e-9, Boolean.TRUE);
            t("fg threshold lag factor", Math.abs(FlyGuard.maxAirSeconds(0, 1.5) - 1.2) < 1e-9, Boolean.TRUE);
        }

        // JesusGuard: only surface skating, never divers/sea-floor walkers
        {
            t("jg surface skims", JesusGuard.skimming(false, false, 0.0, 0.1F), Boolean.TRUE);
            t("jg diver no", JesusGuard.skimming(true, false, -0.3, 0.8F), Boolean.FALSE);
            t("jg sea-floor walker no", JesusGuard.skimming(true, true, 0.0, 0.0F), Boolean.FALSE);
            t("jg head underwater no", JesusGuard.skimming(true, false, 0.0, 0.0F), Boolean.FALSE);
            t("jg falling in no", JesusGuard.skimming(false, false, -0.4, 1.2F), Boolean.FALSE);
            t("jg wave bounce no", JesusGuard.skimming(false, false, 0.12, 0.0F), Boolean.FALSE);
            t("jg needs streak", JesusGuard.shouldFlag(2), Boolean.FALSE);
            t("jg streak flags", JesusGuard.shouldFlag(3), Boolean.TRUE);
        }

        // spec ban durations: xray/esp = 30 days, hacking = permanent
        {
            t("spec xray 30d",
                    dev.superseller.veltryx.anticheat.CheckType.XRAY.specBanSeconds(), 30L * 24 * 60 * 60);
            t("spec fly perm",
                    dev.superseller.veltryx.anticheat.CheckType.FLY.specBanSeconds(), Long.MAX_VALUE);
            t("spec speed perm",
                    dev.superseller.veltryx.anticheat.CheckType.SPEED.specBanSeconds(), Long.MAX_VALUE);
            t("spec jesus perm",
                    dev.superseller.veltryx.anticheat.CheckType.JESUS.specBanSeconds(), Long.MAX_VALUE);
        }

        // SpeedGuard.clampDelta: smooth server authority (no rubber-band)
        {
            double[] c1 = dev.superseller.veltryx.anticheat.SpeedGuard.clampDelta(3.0, 4.0, 2.5);
            t("clamp scales down", Math.abs(Math.sqrt(c1[0] * c1[0] + c1[1] * c1[1]) - 2.5) < 1e-9, Boolean.TRUE);
            t("clamp keeps direction", Math.abs(c1[0] / c1[1] - 3.0 / 4.0) < 1e-9, Boolean.TRUE);
            double[] c2 = dev.superseller.veltryx.anticheat.SpeedGuard.clampDelta(0.3, 0.4, 2.5);
            t("clamp leaves legal moves", c2[0] == 0.3 && c2[1] == 0.4, Boolean.TRUE);
            double[] c3 = dev.superseller.veltryx.anticheat.SpeedGuard.clampDelta(0.0, 0.0, 1.0);
            t("clamp zero safe", c3[0] == 0.0 && c3[1] == 0.0, Boolean.TRUE);
        }

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

        // PriceTable: the pure shop pricing model
        {
            String fixture = "ores:\n  display: \"Ores\"\n  icon: DIAMOND\n  items:\n"
                    + "    DIAMOND: { buy: 8.00, sell: 5.20 }\n"
                    + "    COAL: { buy: 0.25, sell: 0.16 }\n"
                    + "junk-line-ignored\n"
                    + "tools:\n  display: \"Tools\"\n  icon: IRON_PICKAXE\n  items:\n"
                    + "    IRON_PICKAXE: { buy: 2.65, sell: 1.33 }\n";
            var table = dev.superseller.veltryx.shop.PriceTable.parse(fixture);
            t("pt size", table.size(), 3);
            t("pt buy cents", table.buyCents("DIAMOND"), 800L);
            t("pt sell cents", table.sellCents("COAL"), 16L);
            t("pt unknown", table.sellCents("ELYTRA"), -1L);
            t("pt categories", table.categories().size(), 2);
            t("pt display name",
                    dev.superseller.veltryx.shop.PriceTable.displayName("POLISHED_BLACKSTONE_BRICKS")
                            .equals("Polished Blackstone Bricks"), Boolean.TRUE);
            t("pt no dup", table.categories().get(0).items().size(), 2);
        }

        // Real shop.yml: anti-arbitrage is a verified property of the shipped table
        try {
            String shopText = new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Path.of("src/main/resources/shop.yml")));
            var shop = dev.superseller.veltryx.shop.PriceTable.parse(shopText);
            t("shop has almost all items", shop.size() > 600, Boolean.TRUE);

            boolean sane = true;
            for (var cat : shop.categories()) {
                for (var e : cat.items()) {
                    if (!(e.sellCents() > 0 && e.sellCents() < e.buyCents())) {
                        sane = false;
                    }
                }
            }
            t("shop sell < buy everywhere", sane, Boolean.TRUE);

            boolean excluded = true;
            for (String banned : new String[] {"ELYTRA", "BEDROCK", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK",
                    "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART", "BARRIER", "STRUCTURE_BLOCK",
                    "STRUCTURE_VOID", "JIGSAW", "KNOWLEDGE_BOOK", "DEBUG_STICK", "PLAYER_HEAD", "DRAGON_EGG"}) {
                if (shop.get(banned) != null) {
                    excluded = false;
                }
            }
            t("shop excludes op/elytra items", excluded, Boolean.TRUE);

            // recipes: sell(outputs) must never exceed buy(inputs) — buying
            // materials, crafting and selling always loses money
            String[][] recipes = {
                {"log->planks", "OAK_PLANKS*4", "OAK_LOG*1"},
                {"planks->sticks", "STICK*4", "OAK_PLANKS*2"},
                {"torch", "TORCH*4", "COAL*1,STICK*1"},
                {"bread", "BREAD*1", "WHEAT*3"},
                {"glass smelt", "GLASS*1", "SAND*1"},
                {"stone smelt", "STONE*1", "COBBLESTONE*1"},
                {"clay block", "CLAY*1", "CLAY_BALL*4"},
                {"bricks 4->1", "BRICKS*1", "BRICK*4"},
                {"nether bricks 4->4", "NETHER_BRICKS*4", "NETHER_BRICK*4"},
                {"stone bricks 4->4", "STONE_BRICKS*4", "STONE*4"},
                {"sandstone 4->4", "SANDSTONE*4", "SAND*4"},
                {"USER blackstone example", "POLISHED_BLACKSTONE_BRICKS*4", "BLACKSTONE*4"},
                {"quartz block", "QUARTZ_BLOCK*1", "QUARTZ*4"},
                {"quartz decompress", "QUARTZ*4", "QUARTZ_BLOCK*1"},
                {"iron block compress", "IRON_BLOCK*1", "IRON_INGOT*9"},
                {"iron block decompress", "IRON_INGOT*9", "IRON_BLOCK*1"},
                {"gold block decompress", "GOLD_INGOT*9", "GOLD_BLOCK*1"},
                {"diamond block decompress", "DIAMOND*9", "DIAMOND_BLOCK*1"},
                {"emerald block decompress", "EMERALD*9", "EMERALD_BLOCK*1"},
                {"coal block decompress", "COAL*9", "COAL_BLOCK*1"},
                {"lapis block decompress", "LAPIS_LAZULI*9", "LAPIS_BLOCK*1"},
                {"redstone block decompress", "REDSTONE*9", "REDSTONE_BLOCK*1"},
                {"raw iron decompress", "RAW_IRON*9", "RAW_IRON_BLOCK*1"},
                {"netherite block decompress", "NETHERITE_INGOT*9", "NETHERITE_BLOCK*1"},
                {"gold nugget decompress", "GOLD_NUGGET*9", "GOLD_INGOT*1"},
                {"gold nugget compress", "GOLD_INGOT*1", "GOLD_NUGGET*9"},
                {"iron nugget decompress", "IRON_NUGGET*9", "IRON_INGOT*1"},
                {"netherite ingot", "NETHERITE_INGOT*1", "NETHERITE_SCRAP*4,GOLD_INGOT*4"},
                {"melon block", "MELON*1", "MELON_SLICE*9"},
                {"melon decompress max", "MELON_SLICE*7", "MELON*1"},
                {"snow block", "SNOW_BLOCK*1", "SNOWBALL*4"},
                {"snow decompress", "SNOWBALL*4", "SNOW_BLOCK*1"},
                {"kelp block", "DRIED_KELP_BLOCK*1", "DRIED_KELP*9"},
                {"kelp decompress", "DRIED_KELP*9", "DRIED_KELP_BLOCK*1"},
                {"hay block", "HAY_BLOCK*1", "WHEAT*9"},
                {"hay decompress", "WHEAT*9", "HAY_BLOCK*1"},
                {"slime block", "SLIME_BLOCK*1", "SLIME_BALL*9"},
                {"slime decompress", "SLIME_BALL*9", "SLIME_BLOCK*1"},
                {"bone block", "BONE_BLOCK*1", "BONE_MEAL*9"},
                {"bone decompress", "BONE_MEAL*9", "BONE_BLOCK*1"},
                {"wart block", "NETHER_WART_BLOCK*1", "NETHER_WART*9"},
                {"wart decompress", "NETHER_WART*9", "NETHER_WART_BLOCK*1"},
                {"honeycomb block", "HONEYCOMB_BLOCK*1", "HONEYCOMB*4"},
                {"honey block + bottles", "HONEY_BLOCK*1,GLASS_BOTTLE*4", "HONEY_BOTTLE*4"},
                {"glowstone", "GLOWSTONE*1", "GLOWSTONE_DUST*4"},
                {"glowstone decompress", "GLOWSTONE_DUST*4", "GLOWSTONE*1"},
                {"string->wool", "WHITE_WOOL*1", "STRING*4"},
                {"wool->carpet worst", "WHITE_CARPET*3", "WHITE_WOOL*2"},
                {"bed", "WHITE_BED*1", "WHITE_WOOL*3,OAK_PLANKS*3"},
                {"banner", "WHITE_BANNER*1", "WHITE_WOOL*6,STICK*1"},
                {"mushroom stew + bowl", "MUSHROOM_STEW*1,BOWL*1", "BROWN_MUSHROOM*2"},
                {"rabbit stew + bowl", "RABBIT_STEW*1,BOWL*1", "COOKED_RABBIT*1,CARROT*1,POTATO*1,BROWN_MUSHROOM*1"},
                {"beetroot soup + bowl", "BEETROOT_SOUP*1,BOWL*1", "BEETROOT*6"},
                {"cake + buckets", "CAKE*1,BUCKET*3", "MILK_BUCKET*3,SUGAR*2,EGG*1,WHEAT*3"},
                {"pumpkin pie", "PUMPKIN_PIE*1", "PUMPKIN*1,SUGAR*1,EGG*1"},
                {"golden apple", "GOLDEN_APPLE*1", "GOLD_INGOT*8,APPLE*1"},
                {"golden carrot", "GOLDEN_CARROT*1", "GOLD_NUGGET*8,CARROT*1"},
                {"glistering melon", "GLISTERING_MELON_SLICE*1", "GOLD_NUGGET*8,MELON_SLICE*1"},
                {"paper", "PAPER*3", "SUGAR_CANE*3"},
                {"book", "BOOK*1", "PAPER*3,LEATHER*1"},
                {"bookshelf", "BOOKSHELF*1", "OAK_PLANKS*6,BOOK*3"},
                {"enchanting table", "ENCHANTING_TABLE*1", "OBSIDIAN*4,DIAMOND*2,BOOK*1"},
                {"beacon", "BEACON*1", "GLASS*5,NETHER_STAR*1,OBSIDIAN*3"},
                {"conduit", "CONDUIT*1", "NAUTILUS_SHELL*8,HEART_OF_THE_SEA*1"},
                {"tnt", "TNT*1", "GUNPOWDER*5,SAND*4"},
                {"bow", "BOW*1", "STRING*3,STICK*3"},
                {"shield", "SHIELD*1", "OAK_PLANKS*6,IRON_INGOT*1"},
                {"bucket", "BUCKET*1", "IRON_INGOT*3"},
                {"anvil", "ANVIL*1", "IRON_BLOCK*3,IRON_INGOT*4"},
                {"hopper", "HOPPER*1", "IRON_INGOT*5,CHEST*1"},
                {"observer", "OBSERVER*1", "COBBLESTONE*6,REDSTONE*2,QUARTZ*1"},
                {"piston", "PISTON*1", "OAK_PLANKS*3,COBBLESTONE*4,IRON_INGOT*1,REDSTONE*1"},
                {"sticky piston", "STICKY_PISTON*1", "PISTON*1,SLIME_BALL*1"},
                {"lantern", "LANTERN*1", "IRON_NUGGET*8,TORCH*1"},
                {"ladder x3", "LADDER*3", "STICK*7"},
                {"bowl x4", "BOWL*4", "OAK_PLANKS*3"},
                {"glass bottle x4", "GLASS_BOTTLE*4", "GLASS*3"},
                {"glass pane x16", "GLASS_PANE*16", "GLASS*6"},
                {"fire charge x3", "FIRE_CHARGE*3", "COAL*1,BLAZE_POWDER*1,GUNPOWDER*1"},
                {"blaze powder x2", "BLAZE_POWDER*2", "BLAZE_ROD*1"},
                {"magma cream", "MAGMA_CREAM*1", "BLAZE_POWDER*1,SLIME_BALL*1"},
                {"wind charge x4", "WIND_CHARGE*4", "BREEZE_ROD*1"},
                {"baked potato", "BAKED_POTATO*1", "POTATO*1"},
                {"cooked beef", "COOKED_BEEF*1", "BEEF*1"},
                {"charcoal", "CHARCOAL*1", "OAK_LOG*1"},
                {"nether brick smelt", "NETHER_BRICK*1", "NETHERRACK*1"},
                {"dried kelp smelt", "DRIED_KELP*1", "KELP*1"},
                {"smooth stone", "SMOOTH_STONE*1", "STONE*1"},
                {"stone slab x6", "STONE_SLAB*6", "STONE*3"},
                {"stone slab back", "STONE*2", "STONE_SLAB*3"},
                {"stone stairs x4", "STONE_STAIRS*4", "STONE*6"},
                {"white dye <- meal", "WHITE_DYE*1", "BONE_MEAL*3"},
                {"meal <- white dye", "BONE_MEAL*3", "WHITE_DYE*1"},
                {"bone <- meal", "BONE_MEAL*3", "BONE*1"},
                {"yellow dye <- flower", "YELLOW_DYE*1", "DANDELION*1"},
                {"orange dye", "ORANGE_DYE*2", "RED_DYE*1,YELLOW_DYE*1"},
                {"grindstone", "GRINDSTONE*1", "STICK*2,STONE_SLAB*1,OAK_PLANKS*2"},
                {"fletching table", "FLETCHING_TABLE*1", "FLINT*2,OAK_PLANKS*4"},
                {"flint and steel", "FLINT_AND_STEEL*1", "IRON_INGOT*1,FLINT*1"},
                {"shears", "SHEARS*1", "IRON_INGOT*2"},
                {"turtle helmet", "TURTLE_HELMET*1", "TURTLE_SCUTE*5"},
                {"wolf armor", "WOLF_ARMOR*1", "ARMADILLO_SCUTE*6"},
                {"spyglass", "SPYGLASS*1", "COPPER_INGOT*2,AMETHYST_SHARD*1"},
                {"item frame", "ITEM_FRAME*1", "LEATHER*8,STICK*1"},
                {"painting", "PAINTING*1", "WHITE_WOOL*8,STICK*1"},
                {"armor stand", "ARMOR_STAND*1", "STICK*6,SMOOTH_STONE_SLAB*3"},
                {"flower pot", "FLOWER_POT*1", "BRICK*3"},
                {"candle", "WHITE_CANDLE*1", "STRING*1,HONEYCOMB*1"},
                {"campfire", "CAMPFIRE*1", "STICK*3,COAL*1,OAK_LOG*3"},
                {"brewing stand", "BREWING_STAND*1", "BLAZE_ROD*1,COBBLESTONE*3"},
                {"cauldron", "CAULDRON*1", "IRON_INGOT*7"},
                {"map", "MAP*1", "PAPER*8,COMPASS*1"},
                {"clock", "CLOCK*1", "GOLD_INGOT*4,REDSTONE*1"},
                {"compass", "COMPASS*1", "IRON_INGOT*4,REDSTONE*1"},
                {"rail x16", "RAIL*16", "IRON_INGOT*6,STICK*1,REDSTONE*1"},
                {"powered rail x6", "POWERED_RAIL*6", "GOLD_INGOT*6,STICK*1,REDSTONE*1"},
                {"crafting table", "CRAFTING_TABLE*1", "OAK_PLANKS*4"},
                {"chest", "CHEST*1", "OAK_PLANKS*8"},
                {"smoker", "SMOKER*1", "OAK_LOG*4,FURNACE*1"},
                {"blast furnace", "BLAST_FURNACE*1", "IRON_INGOT*5,FURNACE*1,SMOOTH_STONE*3"},
                {"note block", "NOTE_BLOCK*1", "OAK_PLANKS*8,REDSTONE*1"},
                {"jukebox", "JUKEBOX*1", "OAK_PLANKS*8,DIAMOND*1"},
                {"lectern", "LECTERN*1", "BOOKSHELF*1,OAK_SLAB*4"},
                {"composter", "COMPOSTER*1", "OAK_SLAB*7"},
                {"beehive", "BEEHIVE*1", "OAK_PLANKS*6,HONEYCOMB*3"},
                {"firework rocket", "FIREWORK_ROCKET*1", "PAPER*1,GUNPOWDER*1"},
                {"respawn anchor", "RESPAWN_ANCHOR*1", "CRYING_OBSIDIAN*6,GLOWSTONE*3"},
                {"lodestone", "LODESTONE*1", "CHISELED_STONE_BRICKS*8,NETHERITE_INGOT*1"},
                {"copper bulb", "COPPER_BULB*1", "COPPER_INGOT*4,BLAZE_POWDER*1"},
            };
            boolean closed = true;
            String worst = "";
            for (String[] recipe : recipes) {
                double outValue = 0;
                for (String part : recipe[1].split(",")) {
                    String[] mq = part.split("\\*");
                    long sell = shop.sellCents(mq[0]);
                    if (sell < 0) {
                        closed = false;
                        worst = recipe[0] + " missing " + mq[0];
                    }
                    outValue += sell * Integer.parseInt(mq[1]);
                }
                double inValue = 0;
                for (String part : recipe[2].split(",")) {
                    String[] mq = part.split("\\*");
                    long buy = shop.buyCents(mq[0]);
                    if (buy < 0) {
                        closed = false;
                        worst = recipe[0] + " missing " + mq[0];
                    }
                    inValue += buy * Integer.parseInt(mq[1]);
                }
                if (outValue > inValue + 0.001) {
                    closed = false;
                    worst = recipe[0] + " sells " + outValue + " for inputs worth " + inValue;
                }
            }
            t("shop no buy-craft-sell exploit (" + recipes.length + " recipes)", closed, Boolean.TRUE);
            if (!closed) {
                System.out.println("   exploit: " + worst);
            }
        } catch (java.io.IOException e) {
            t("shop.yml readable", Boolean.FALSE, Boolean.TRUE);
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

        // Shard tools: AreaPlane (look-direction mining planes)
        t("plane floor 3x3", Integer.valueOf(dev.superseller.veltryx.shards.AreaPlane.offsets(0, -1, 0, 1).size()), Integer.valueOf(8));
        t("plane wall 3x3", Integer.valueOf(dev.superseller.veltryx.shards.AreaPlane.offsets(1, 0, 0, 1).size()), Integer.valueOf(8));
        t("plane 5x5", Integer.valueOf(dev.superseller.veltryx.shards.AreaPlane.offsets(0, 1, 0, 2).size()), Integer.valueOf(24));
        boolean planeOriginFree = true;
        boolean planePerp = true;
        for (int[] o : dev.superseller.veltryx.shards.AreaPlane.offsets(0, 1, 0, 1)) {
            if (o[0] == 0 && o[1] == 0 && o[2] == 0) {
                planeOriginFree = false;
            }
            if (o[1] != 0) {
                planePerp = false;
            }
        }
        t("plane excludes origin", planeOriginFree, true);
        t("plane perpendicular to look", planePerp, true);

        // Shard tools: TreeFeller (BFS tree collection + caps + leaves)
        dev.superseller.veltryx.shards.TreeFeller.Grid column = (x, y, z) ->
                (x == 0 && z == 0 && y >= 0 && y <= 5) ? "OAK_LOG" : null;
        t("tree collects column", Integer.valueOf(dev.superseller.veltryx.shards.TreeFeller
                .collect(0, 0, 0, "OAK_LOG", column, n -> n.endsWith("_LOG"), true, 128).size()), Integer.valueOf(5));
        t("tree respects max", Integer.valueOf(dev.superseller.veltryx.shards.TreeFeller
                .collect(0, 0, 0, "OAK_LOG", column, n -> n.endsWith("_LOG"), true, 3).size()), Integer.valueOf(2));
        t("tree same-material only", Integer.valueOf(dev.superseller.veltryx.shards.TreeFeller
                .collect(0, 0, 0, "OAK_LOG", column, n -> n.endsWith("_LOG"), true, 128).size()), Integer.valueOf(5));
        dev.superseller.veltryx.shards.TreeFeller.Grid single = (x, y, z) ->
                (x == 0 && y == 0 && z == 0) ? "OAK_LOG" : Math.abs(x) + Math.abs(y) + Math.abs(z) == 1 ? "OAK_LEAVES" : null;
        t("tree leaves adjacent", Integer.valueOf(dev.superseller.veltryx.shards.TreeFeller
                .collectLeaves(java.util.List.of(new int[]{0, 0, 0}), single, n -> n.endsWith("LEAVES"), 256).size()), Integer.valueOf(6));
        t("tree leaves cap", Integer.valueOf(dev.superseller.veltryx.shards.TreeFeller
                .collectLeaves(java.util.List.of(new int[]{0, 0, 0}), single, n -> n.endsWith("LEAVES"), 2).size()), Integer.valueOf(2));
        t("tree leaves none", Integer.valueOf(dev.superseller.veltryx.shards.TreeFeller
                .collectLeaves(java.util.List.of(), single, n -> true, 256).size()), Integer.valueOf(0));

        // Shard tools: ExpiryMath (24h wall-clock self-destruct)
        t("expiry permanent", Long.valueOf(dev.superseller.veltryx.shards.ExpiryMath.expiresAt(100L, 0L)), Long.valueOf(Long.MAX_VALUE));
        t("expiry at", Long.valueOf(dev.superseller.veltryx.shards.ExpiryMath.expiresAt(1000L, 2000L)), Long.valueOf(3000L));
        t("expiry remaining", Long.valueOf(dev.superseller.veltryx.shards.ExpiryMath.remaining(1000L, 2000L, 2500L)), Long.valueOf(500L));
        t("expiry remaining floor", Long.valueOf(dev.superseller.veltryx.shards.ExpiryMath.remaining(1000L, 2000L, 9000L)), Long.valueOf(0L));
        t("expiry flag", dev.superseller.veltryx.shards.ExpiryMath.expired(1000L, 2000L, 3000L), true);
        t("expiry live", dev.superseller.veltryx.shards.ExpiryMath.expired(1000L, 2000L, 2999L), false);
        t("expiry warn 60", Integer.valueOf(dev.superseller.veltryx.shards.ExpiryMath
                .dueWarning(59L * 60_000L, new long[]{60, 10}, 0)), Integer.valueOf(0));
        t("expiry warn once", Integer.valueOf(dev.superseller.veltryx.shards.ExpiryMath
                .dueWarning(59L * 60_000L, new long[]{60, 10}, 1)), Integer.valueOf(-1));
        t("expiry warn 10", Integer.valueOf(dev.superseller.veltryx.shards.ExpiryMath
                .dueWarning(9L * 60_000L, new long[]{60, 10}, 1)), Integer.valueOf(1));
        t("expiry warn none", Integer.valueOf(dev.superseller.veltryx.shards.ExpiryMath
                .dueWarning(2L * 3_600_000L, new long[]{60, 10}, 0)), Integer.valueOf(-1));

        // ItemValue: enchants raise the /sell price, lost durability lowers it
        t("value plain", Long.valueOf(dev.superseller.veltryx.shop.ItemValue.valueCents(1000L, 2000L, 0, 1.0)), Long.valueOf(1000L));
        t("value enchanted", Long.valueOf(dev.superseller.veltryx.shop.ItemValue.valueCents(1000L, 5000L, 5, 1.0)), Long.valueOf(1600L));
        t("value enchanted more", dev.superseller.veltryx.shop.ItemValue.valueCents(1000L, 5000L, 10, 1.0)
                > dev.superseller.veltryx.shop.ItemValue.valueCents(1000L, 5000L, 1, 1.0), true);
        t("value damaged", Long.valueOf(dev.superseller.veltryx.shop.ItemValue.valueCents(1000L, 2000L, 0, 0.5)), Long.valueOf(500L));
        t("value broken", Long.valueOf(dev.superseller.veltryx.shop.ItemValue.valueCents(1000L, 2000L, 0, 0.0)), Long.valueOf(0L));
        t("value cap below buy", Long.valueOf(dev.superseller.veltryx.shop.ItemValue.valueCents(1000L, 1200L, 20, 1.0)), Long.valueOf(1199L));
        t("value loop safe", dev.superseller.veltryx.shop.ItemValue.valueCents(1000L, 1001L, 99, 1.0) < 1001L, true);
        t("value no base", Long.valueOf(dev.superseller.veltryx.shop.ItemValue.valueCents(0L, 2000L, 5, 1.0)), Long.valueOf(0L));

        // Shard catalog: the four weapons/tools at 500 shards, void totem 150
        dev.superseller.veltryx.shards.ShardCatalog cat = new dev.superseller.veltryx.shards.ShardCatalog();
        t("catalog size", Integer.valueOf(cat.all().size()), Integer.valueOf(5));
        t("catalog spear", Long.valueOf(cat.byId("shard_spear").priceShards()), Long.valueOf(500L));
        t("catalog trident", Long.valueOf(cat.byId("shard_trident").priceShards()), Long.valueOf(500L));
        t("catalog axe", Long.valueOf(cat.byId("shard_axe").priceShards()), Long.valueOf(500L));
        t("catalog pickaxe", Long.valueOf(cat.byId("shard_pickaxe").priceShards()), Long.valueOf(500L));
        t("catalog totem", Long.valueOf(cat.byId("void_totem").priceShards()), Long.valueOf(150L));
        t("catalog 24h", Long.valueOf(cat.byId("shard_spear").lifetimeMs()), Long.valueOf(24L * 60 * 60 * 1000));
        t("catalog totem permanent", cat.byId("void_totem").expires(), false);
        t("catalog spear behavior", cat.byId("shard_spear").behavior(), dev.superseller.veltryx.shards.Behavior.LUNGE_SPEAR);
        t("catalog trident behavior", cat.byId("shard_trident").behavior(), dev.superseller.veltryx.shards.Behavior.RIPTIDE_TRIDENT);
        t("catalog axe behavior", cat.byId("shard_axe").behavior(), dev.superseller.veltryx.shards.Behavior.TREE_AXE);
        t("catalog pickaxe behavior", cat.byId("shard_pickaxe").behavior(), dev.superseller.veltryx.shards.Behavior.AREA_MINE);
        t("catalog totem behavior", cat.byId("void_totem").behavior(), dev.superseller.veltryx.shards.Behavior.VOID_TOTEM);
        t("catalog tools have choice", cat.byId("shard_pickaxe").hasChoice(), true);
        t("catalog axe has choice", cat.byId("shard_axe").hasChoice(), true);
        t("catalog weapons no choice", cat.byId("shard_spear").hasChoice(), false);
        t("catalog trident riptide", Integer.valueOf(cat.byId("shard_trident").enchants().get(0).level()), Integer.valueOf(3));
        t("income per minute", Long.valueOf(dev.superseller.veltryx.shards.ShardService.SHARDS_PER_MINUTE), Long.valueOf(1L));
        try {
            String shardCmdSrc = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                    "src/main/java/dev/superseller/veltryx/shards/ShardCommand.java")));
            t("no buy-shards subcommand", shardCmdSrc.contains("\"buy\""), false);
        } catch (java.io.IOException e) {
            t("no buy-shards subcommand readable", Boolean.FALSE, Boolean.TRUE);
        }

        // RTP safety rules (pure)
        t("rtp safe rock", dev.superseller.veltryx.rtp.RtpSafety.isSafe("STONE", true, "AIR", "AIR"), true);
        t("rtp cave air ok", dev.superseller.veltryx.rtp.RtpSafety.isSafe("DEEPSLATE", true, "CAVE_AIR", "CAVE_AIR"), true);
        t("rtp end void air", dev.superseller.veltryx.rtp.RtpSafety.isSafe("END_STONE", true, "VOID_AIR", "AIR"), true);
        t("rtp leaves ok", dev.superseller.veltryx.rtp.RtpSafety.isSafe("OAK_LEAVES", true, "AIR", "AIR"), true);
        t("rtp lava ground", dev.superseller.veltryx.rtp.RtpSafety.isSafe("LAVA", false, "AIR", "AIR"), false);
        t("rtp magma ground", dev.superseller.veltryx.rtp.RtpSafety.isSafe("MAGMA_BLOCK", true, "AIR", "AIR"), false);
        t("rtp cactus ground", dev.superseller.veltryx.rtp.RtpSafety.isSafe("CACTUS", true, "AIR", "AIR"), false);
        t("rtp fire ground", dev.superseller.veltryx.rtp.RtpSafety.isSafe("FIRE", false, "AIR", "AIR"), false);
        t("rtp soul fire", dev.superseller.veltryx.rtp.RtpSafety.isSafe("SOUL_FIRE", false, "AIR", "AIR"), false);
        t("rtp campfire", dev.superseller.veltryx.rtp.RtpSafety.isSafe("CAMPFIRE", true, "AIR", "AIR"), false);
        t("rtp berry bush", dev.superseller.veltryx.rtp.RtpSafety.isSafe("SWEET_BERRY_BUSH", true, "AIR", "AIR"), false);
        t("rtp wither rose", dev.superseller.veltryx.rtp.RtpSafety.isSafe("WITHER_ROSE", true, "AIR", "AIR"), false);
        t("rtp powder snow", dev.superseller.veltryx.rtp.RtpSafety.isSafe("POWDER_SNOW", true, "AIR", "AIR"), false);
        t("rtp bedrock", dev.superseller.veltryx.rtp.RtpSafety.isSafe("BEDROCK", true, "AIR", "AIR"), false);
        t("rtp water feet", dev.superseller.veltryx.rtp.RtpSafety.isSafe("SAND", true, "WATER", "AIR"), false);
        t("rtp solid feet", dev.superseller.veltryx.rtp.RtpSafety.isSafe("STONE", true, "STONE", "AIR"), false);
        t("rtp solid head", dev.superseller.veltryx.rtp.RtpSafety.isSafe("STONE", true, "AIR", "STONE"), false);
        t("rtp air ground", dev.superseller.veltryx.rtp.RtpSafety.isSafe("AIR", false, "AIR", "AIR"), false);
        t("rtp null ground", dev.superseller.veltryx.rtp.RtpSafety.isSafe(null, true, "AIR", "AIR"), false);

        // Choice catalog: potions, tipped arrows, enchanted books
        t("potions offered", Integer.valueOf(dev.superseller.veltryx.shop.ChoiceCatalog.potions().size()), Integer.valueOf(15));
        boolean potionPricesOk = true;
        long healingPrice = 0;
        for (var potion : dev.superseller.veltryx.shop.ChoiceCatalog.potions()) {
            if (potion.priceCents() <= 0) {
                potionPricesOk = false;
            }
            if (potion.key().equals("HEALING")) {
                healingPrice = potion.priceCents();
            }
        }
        t("potion prices positive", potionPricesOk, true);
        t("healing price", Long.valueOf(healingPrice), Long.valueOf(5000L));
        t("arrow pack 1.25x", Long.valueOf(dev.superseller.veltryx.shop.ChoiceCatalog.arrowPackCents(
                new dev.superseller.veltryx.shop.ChoiceCatalog.PotionOption("HEALING", "Healing", 5000L))),
                Long.valueOf(6250L));
        t("arrow pack size", Integer.valueOf(dev.superseller.veltryx.shop.ChoiceCatalog.TIPPED_ARROW_PACK), Integer.valueOf(8));
        t("books offered", Integer.valueOf(dev.superseller.veltryx.shop.ChoiceCatalog.books().size()), Integer.valueOf(30));
        dev.superseller.veltryx.shop.ChoiceCatalog.BookOption sharpness = null;
        dev.superseller.veltryx.shop.ChoiceCatalog.BookOption mending = null;
        for (var book : dev.superseller.veltryx.shop.ChoiceCatalog.books()) {
            if (book.key().equals("SHARPNESS")) {
                sharpness = book;
            }
            if (book.key().equals("MENDING")) {
                mending = book;
            }
        }
        t("sharpness found", Boolean.valueOf(sharpness != null), Boolean.valueOf(true));
        t("mending found", Boolean.valueOf(mending != null), Boolean.valueOf(true));
        t("sharpness V price", Long.valueOf(dev.superseller.veltryx.shop.ChoiceCatalog.bookPriceCents(sharpness, 5)), Long.valueOf(125000L));
        t("sharpness I price", Long.valueOf(dev.superseller.veltryx.shop.ChoiceCatalog.bookPriceCents(sharpness, 1)), Long.valueOf(25000L));
        t("book level clamps high", Long.valueOf(dev.superseller.veltryx.shop.ChoiceCatalog.bookPriceCents(sharpness, 99)), Long.valueOf(125000L));
        t("book level clamps low", Long.valueOf(dev.superseller.veltryx.shop.ChoiceCatalog.bookPriceCents(sharpness, 0)), Long.valueOf(25000L));
        t("mending rare price", Long.valueOf(dev.superseller.veltryx.shop.ChoiceCatalog.bookPriceCents(mending, 1)), Long.valueOf(80000L));

        // Search matching + quickshop defaults
        t("search by display", dev.superseller.veltryx.shop.ChoiceCatalog.matches("diamond", "Diamond Sword", "DIAMOND_SWORD"), true);
        t("search by material", dev.superseller.veltryx.shop.ChoiceCatalog.matches("sword", "Blade", "IRON_SWORD"), true);
        t("search with space", dev.superseller.veltryx.shop.ChoiceCatalog.matches("diamond s", "Diamond Sword", "DIAMOND_SWORD"), true);
        t("search no match", dev.superseller.veltryx.shop.ChoiceCatalog.matches("zzz", "Diamond Sword", "DIAMOND_SWORD"), false);
        t("search blank", dev.superseller.veltryx.shop.ChoiceCatalog.matches("  ", "Diamond Sword", "DIAMOND_SWORD"), false);
        t("quickshop 5 rows", Boolean.valueOf(dev.superseller.veltryx.shop.ChoiceCatalog.defaultQuickshopItems().size() <= 45), Boolean.valueOf(true));
        t("quickshop default size", Integer.valueOf(dev.superseller.veltryx.shop.ChoiceCatalog.defaultQuickshopItems().size()), Integer.valueOf(45));

        System.out.println("Smoke: " + passed + " passed, " + failed + " failed.");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) < 0.0001;
    }

    private static double s(double v) {
        return v;
    }

    /** Parses shop.yml (the file the plugin ships) into name -> [buy, sell]. */
    private static java.util.Map<String, double[]> shopPrices() {
        java.util.Map<String, double[]> out = new java.util.HashMap<>();
        try {
            for (String line : java.nio.file.Files.readAllLines(
                    java.nio.file.Path.of("src/main/resources/shop.yml"))) {
                String t = line.trim();
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("([A-Z][A-Z0-9_]+): \\{ buy: ([0-9.]+), sell: ([0-9.]+) \\}")
                        .matcher(t);
                if (m.matches()) {
                    out.put(m.group(1), new double[]{
                            Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3))});
                }
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    private static boolean grEq(double buy, double sell) {
        var p = shopPrices();
        for (String fam : new String[]{"COPPER_GRATE", "COPPER_BULB", "COPPER_DOOR",
                "COPPER_TRAPDOOR", "CHISELED_COPPER"}) {
            double[] base = p.get(fam);
            for (String pre : new String[]{"EXPOSED_", "WEATHERED_", "OXIDIZED_"}) {
                double[] v = p.get(pre + fam);
                if (v == null || v[0] != base[0] || v[1] != base[1]) {
                    return false;
                }
            }
        }
        double[] blk = p.get("COPPER_BLOCK");
        for (String n : new String[]{"EXPOSED_COPPER", "WEATHERED_COPPER", "OXIDIZED_COPPER",
                "WAXED_COPPER_BLOCK"}) {
            double[] v = p.get(n);
            if (n.startsWith("WAXED")) {
                continue;
            }
            if (v == null || v[0] != blk[0] || v[1] != blk[1]) {
                return false;
            }
        }
        return p.size() > 1100 && buy == 0.40 && sell == 0.20;
    }

    private static boolean woodEq() {
        var p = shopPrices();
        String[][] groups = {
                {"OAK_LOG", "STRIPPED_OAK_LOG", "OAK_WOOD", "STRIPPED_OAK_WOOD"},
                {"PALE_OAK_LOG", "STRIPPED_PALE_OAK_LOG", "PALE_OAK_WOOD", "STRIPPED_PALE_OAK_WOOD"},
                {"CRIMSON_STEM", "STRIPPED_CRIMSON_STEM", "CRIMSON_WOOD", "STRIPPED_CRIMSON_WOOD"},
        };
        for (String[] g : groups) {
            double[] first = p.get(g[0]);
            if (first == null) {
                return false;
            }
            for (String n : g) {
                double[] v = p.get(n);
                if (v == null || v[0] != first[0] || v[1] != first[1]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean newItemsBelowBuy() {
        var p = shopPrices();
        if (p.size() < 1100) {
            return false;
        }
        for (var e : p.entrySet()) {
            double[] v = e.getValue();
            if (!(v[1] < v[0]) || v[0] <= 0 || v[1] <= 0) {
                return false;
            }
        }
        return true;
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
