package dev.superseller.veltryx.config;

import dev.superseller.veltryx.anticheat.CheckType;
import dev.superseller.veltryx.util.Money;
import dev.superseller.veltryx.util.TimeUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed access to {@code config.yml} with sensible defaults for every value,
 * so a partially written config still runs. Reloadable at runtime.
 */
public final class VeltryxConfig {

    /** One configured {@code /offend} reason. */
    public record OffendReason(String key, String display, String category,
                               long durationSeconds, List<String> aliases) {
    }

    private volatile FileConfiguration config;

    public VeltryxConfig(FileConfiguration config) {
        this.config = config;
    }

    /** Swaps in a freshly loaded configuration (see /veltryx reload). */
    public void reload(FileConfiguration fresh) {
        this.config = fresh;
    }

    // ------------------------------------------------------------- storage

    public String storageType() {
        return config.getString("storage.type", "yaml").toLowerCase(Locale.ROOT);
    }

    public long autosaveSeconds() {
        return Math.max(30L, config.getLong("storage.autosave-seconds", 300L));
    }

    // ------------------------------------------------------------- economy

    public String currencySymbol() {
        return config.getString("economy.currency-symbol", "$");
    }

    public String currencyName() {
        return config.getString("economy.currency-name", "dollar");
    }

    public long startingBalance() {
        long parsed = Money.parse(config.getString("economy.starting-balance", "100"));
        return parsed == Money.INVALID ? 10_000L : parsed;
    }

    public long minTransfer() {
        long parsed = Money.parse(config.getString("economy.min-transfer", "0.01"));
        return parsed == Money.INVALID ? 1L : parsed;
    }

    public int baltopSize() {
        return Math.max(1, config.getInt("economy.baltop-size", 10));
    }

    // ------------------------------------------------------------ anticheat

    public boolean anticheatEnabled() {
        return config.getBoolean("anticheat.enabled", true);
    }

    public int maxPing() {
        return Math.max(50, config.getInt("anticheat.max-ping", 350));
    }

    public double minTps() {
        return config.getDouble("anticheat.min-tps", 18.0);
    }

    public int punishMaxPing() {
        return Math.max(50, config.getInt("anticheat.punish-max-ping", 250));
    }

    public double punishMinTps() {
        return config.getDouble("anticheat.punish-min-tps", 19.0);
    }

    public int joinGraceSeconds() {
        return Math.max(0, config.getInt("anticheat.join-grace-seconds", 10));
    }

    public double decayPerSecond() {
        return Math.max(0.0, config.getDouble("anticheat.decay-per-second", 0.5));
    }

    public double maxMoveSpeed() {
        return config.getDouble("anticheat.max-move-speed", 6.5);
    }

    /** Server authority: impossible movement (speed/flight hacks) is rolled back. */
    public boolean serverAuthority() {
        return config.getBoolean("anticheat.server-authority", true);
    }

    /** Movement faster than limit × this is impossible for vanilla clients. */
    public double speedHardCap() {
        return Math.max(1.05, config.getDouble("anticheat.speed-hard-cap", 1.75));
    }

    public double maxCps() {
        return config.getDouble("anticheat.max-cps", 19.0);
    }

    public double maxReach() {
        return config.getDouble("anticheat.max-reach", 3.4);
    }

    public double maxInteractReach() {
        return config.getDouble("anticheat.max-interact-reach", 5.6);
    }

    public int maxBreaksPerSecond() {
        return Math.max(1, config.getInt("anticheat.max-breaks-per-second", 25));
    }

    public int maxPlacesPerSecond() {
        return Math.max(1, config.getInt("anticheat.max-places-per-second", 20));
    }

    public double minAimAngle() {
        return config.getDouble("anticheat.min-aim-angle", 100.0);
    }

    /** Escalation plan for one check, with defaults when the section is absent. */
    public CheckType.Plan plan(CheckType type) {
        String path = "anticheat.checks." + type.configPath();
        boolean enabled = config.getBoolean(path + ".enabled", true);
        double alert = config.getDouble(path + ".alert-vl", defaultAlert(type));
        double kick = config.getDouble(path + ".kick-vl", defaultKick(type));
        double ban = config.getDouble(path + ".ban-vl", defaultBan(type));
        long banSeconds = TimeUtil.parseSeconds(config.getString(path + ".ban-duration", defaultBanDuration(type)));
        if (banSeconds == TimeUtil.INVALID) {
            banSeconds = 604_800L;
        }
        String reason = config.getString(path + ".ban-reason", "Cheating (" + type.display() + ")");
        return new CheckType.Plan(enabled, alert, Math.max(alert, kick), Math.max(kick, ban),
                banSeconds, reason);
    }

    private static double defaultAlert(CheckType type) {
        return type == CheckType.XRAY ? 4.0 : 6.0;
    }

    private static double defaultKick(CheckType type) {
        return switch (type) {
            case FLY -> 18.0;
            case PHASE -> 14.0;
            case XRAY -> 12.0;
            default -> 20.0;
        };
    }

    private static double defaultBan(CheckType type) {
        return switch (type) {
            case FLY -> 40.0;
            case PHASE -> 30.0;
            case XRAY -> 25.0;
            default -> 45.0;
        };
    }

    private static String defaultBanDuration(CheckType type) {
        return type == CheckType.XRAY ? "30d" : "7d";
    }

    // ---------------------------------------------------------------- xray

    // ------------------------------------------------- market / homes / tpa

    /** How long auction listings stay buyable (seconds). */
    public long auctionHours() {
        return config.getLong("market.auction-hours", 48L);
    }

    /** How long buy orders stay open (seconds). */
    public long orderHours() {
        return config.getLong("market.order-hours", 48L);
    }

    /** How many active listings one player may have at once. */
    public int maxListings() {
        return config.getInt("market.max-listings", 20);
    }

    /** How many open buy orders one player may have at once. */
    public int maxOrders() {
        return config.getInt("market.max-orders", 20);
    }

    /** Hard cap of homes per player. */
    public int maxHomes() {
        return config.getInt("home.max", 45);
    }

    /** Homes that are free for everyone. */
    public int freeHomes() {
        return config.getInt("home.free", 3);
    }

    /** Price increase per extra home beyond the free ones (money units). */
    public long homePriceStep() {
        long parsed = Money.parse(config.getString("home.price-step", "2500"));
        return parsed == Money.INVALID ? 250_000L : parsed;
    }

    /** Send cooldown for tpa/tpahere per counterpart (seconds). */
    public long tpaCooldownSeconds() {
        return config.getLong("tpa.cooldown-seconds", 10L);
    }

    /** How long a tpa request stays acceptable (seconds). */
    public long tpaTimeoutSeconds() {
        return config.getLong("tpa.request-timeout-seconds", 60L);
    }

    public boolean xrayObfuscation() {
        return config.getBoolean("anticheat.xray.obfuscation", true);
    }

    public int xrayRevealRadius() {
        return Math.max(2, config.getInt("anticheat.xray.reveal-radius", 5));
    }

    public int xraySweepRadius() {
        return Math.max(4, config.getInt("anticheat.xray.sweep-radius", 12));
    }

    public boolean xrayHideExposed() {
        return config.getBoolean("anticheat.xray.hide-exposed", true);
    }

    public int xrayScanYMin() {
        return config.getInt("anticheat.xray.scan-y-min", -64);
    }

    public int xrayScanYMax() {
        return config.getInt("anticheat.xray.scan-y-max", 80);
    }

    public int xrayUpdateIntervalMs() {
        return Math.max(250, config.getInt("anticheat.xray.update-interval-ms", 1000));
    }

    public double xrayMinRatio() {
        return config.getDouble("anticheat.xray.min-hidden-ratio", 0.55);
    }

    public int xrayMinSamples() {
        return Math.max(3, config.getInt("anticheat.xray.min-samples", 6));
    }

    public int xrayWindow() {
        return Math.max(5, config.getInt("anticheat.xray.window", 20));
    }

    // ---------------------------------------------------------- punishments

    /** All configured /offend reasons, in config order. */
    public List<OffendReason> offendReasons() {
        List<OffendReason> reasons = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("punishments.offend-reasons");
        if (section == null) {
            reasons.addAll(defaultOffendReasons());
            return reasons;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            long seconds = TimeUtil.parseSeconds(s.getString("duration", "perm"));
            if (seconds == TimeUtil.INVALID) {
                seconds = Long.MAX_VALUE;
            }
            reasons.add(new OffendReason(key,
                    s.getString("display", key),
                    s.getString("category", key),
                    seconds,
                    s.getStringList("aliases")));
        }
        return reasons.isEmpty() ? defaultOffendReasons() : reasons;
    }

    private static List<OffendReason> defaultOffendReasons() {
        return List.of(
                new OffendReason("xray", "X-Ray / ESP", "xray-esp", 30L * 86_400L,
                        List.of("esp", "xray/esp", "xray-esp", "xrayesp", "x-raying")),
                new OffendReason("hacking", "Hacking", "hacking", Long.MAX_VALUE,
                        List.of("fly", "flying", "speed", "killaura", "aura", "autoclicker",
                                "reach", "jesus", "bhop", "scaffold", "timer", "phase",
                                "cheating", "cheats", "hacks")));
    }

    /** Resolves user input to a configured reason (primary key or alias). */
    public Optional<OffendReason> resolveOffendReason(String input) {
        String needle = input.trim().toLowerCase(Locale.ROOT);
        for (OffendReason reason : offendReasons()) {
            if (reason.key().equalsIgnoreCase(needle) || reason.display().equalsIgnoreCase(needle)) {
                return Optional.of(reason);
            }
            for (String alias : reason.aliases()) {
                if (alias.equalsIgnoreCase(needle)) {
                    return Optional.of(reason);
                }
            }
        }
        return Optional.empty();
    }

    public String appealUrl() {
        return config.getString("punishments.appeal-url", "discord.gg/yourserver");
    }

    // ----------------------------------------------------------- moderation

    public int warnThreshold() {
        return Math.max(1, config.getInt("moderation.warn-threshold", 3));
    }

    public String warnAction() {
        return config.getString("moderation.warn-action", "kick").toLowerCase(Locale.ROOT);
    }

    public long warnExpirySeconds() {
        long seconds = TimeUtil.parseSeconds(config.getString("moderation.warn-expiry", "30d"));
        return seconds == TimeUtil.INVALID ? 2_592_000L : seconds;
    }

    public long muteDefaultSeconds() {
        long seconds = TimeUtil.parseSeconds(config.getString("moderation.mute-default-duration", "1h"));
        return seconds == TimeUtil.INVALID ? 3_600L : seconds;
    }

    public long flagRetentionSeconds() {
        long seconds = TimeUtil.parseSeconds(config.getString("moderation.flag-retention", "30d"));
        return seconds == TimeUtil.INVALID ? 2_592_000L : seconds;
    }

    // --------------------------------------------------------------- staff

    public String broadcastPrefix() {
        return config.getString("staff.broadcast-prefix",
                "<gradient:#f59e0b:#ef4444><bold>ANNOUNCE</bold></gradient> <dark_gray>»</dark_gray> ");
    }

    public int slowchatDefaultSeconds() {
        return Math.max(1, config.getInt("staff.slowchat-default-seconds", 5));
    }

    public boolean joinVanishRemembers() {
        return config.getBoolean("staff.vanish-persists", true);
    }

    // -------------------------------------------------------------- shards

    /** Passive shard income per minute of playtime (1 = the Veltryx default). */
    public long shardsPerMinute() {
        return Math.max(0L, config.getLong("shards.per-minute", 1L));
    }

    /** Whether the small "you earned X shards" chat line is shown on income. */
    public boolean shardIncomeMessage() {
        return config.getBoolean("shards.income-message", true);
    }

    /** 3x3 plane radius of the shard tools (1 = 3x3, 2 = 5x5). */
    public int shardAreaRadius() {
        return Math.max(1, Math.min(2, config.getInt("shards.area-radius", 1)));
    }

    /** Hard cap of blocks a tree felling may break (log chain limit). */
    public int shardTreeMaxBlocks() {
        return Math.max(16, config.getInt("shards.tree.max-blocks", 128));
    }

    /** Whether the shard axe also breaks the tree's leaves. */
    public boolean shardTreeBreakLeaves() {
        return config.getBoolean("shards.tree.break-leaves", true);
    }

    /** Whether the shard axe replants a sapling where the tree stood. */
    public boolean shardTreeReplant() {
        return config.getBoolean("shards.tree.replant", true);
    }

    /** Haste II duration of the shard potion, in minutes. */
    public long shardHasteMinutes() {
        return Math.max(1L, config.getLong("shards.haste-minutes", 60L));
    }

    /** Whole config, for the consistency checker and debugging. */
    public Map<String, Object> rawValues() {
        return new LinkedHashMap<>(config.getValues(true));
    }
}
