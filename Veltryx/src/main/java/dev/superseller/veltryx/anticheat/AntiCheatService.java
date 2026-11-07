package dev.superseller.veltryx.anticheat;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.model.FlagEntry;
import dev.superseller.veltryx.punish.BanService;
import dev.superseller.veltryx.storage.DataStore;
import dev.superseller.veltryx.util.Ids;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.TimeUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * The anti-cheat brain: violation levels, staff alerts and the automatic
 * escalation to kicks and bans.
 *
 * <p>False-positive safety is layered:
 * <ul>
 *   <li>checks themselves are skipped on high ping / low TPS
 *       ({@link PingGuard#shouldSkip});</li>
 *   <li>tolerances scale with ping and TPS ({@link PingGuard#allowanceFactor});</li>
 *   <li>kicks and bans additionally require healthy conditions
 *       ({@link PingGuard#reliable});</li>
 *   <li>a join grace period gives clients time to sync;</li>
 *   <li>violation levels decay continuously, so one-off lag spikes never
 *       accumulate into a punishment.</li>
 * </ul>
 */
public final class AntiCheatService {

    private final DataStore store;
    private final Messages messages;
    private final VeltryxConfig config;
    private final BanService bans;

    private final Map<UUID, PlayerTrace> traces = new ConcurrentHashMap<>();
    private volatile Map<CheckType, CheckType.Plan> plans = Map.of();
    private long lastDecayMillis = System.currentTimeMillis();

    public AntiCheatService(DataStore store, Messages messages, VeltryxConfig config, BanService bans) {
        this.store = store;
        this.messages = messages;
        this.config = config;
        this.bans = bans;
        reload();
    }

    /** Rebuilds the escalation plans from the config. */
    public void reload() {
        java.util.EnumMap<CheckType, CheckType.Plan> loaded = new java.util.EnumMap<>(CheckType.class);
        for (CheckType type : CheckType.values()) {
            loaded.put(type, config.plan(type));
        }
        plans = loaded;
    }

    public CheckType.Plan plan(CheckType type) {
        return plans.getOrDefault(type, new CheckType.Plan(true, 6, 20, 45, 604_800L, "Cheating"));
    }

    // ------------------------------------------------------------ lifecycle

    /** Opens a session trace for a joining player. */
    public void startTracking(Player player) {
        traces.put(player.getUniqueId(),
                new PlayerTrace(player.getUniqueId(), player.getName(), System.currentTimeMillis()));
    }

    /** Closes the session trace of a leaving player. */
    public void stopTracking(UUID uuid) {
        traces.remove(uuid);
    }

    /** The live trace of a player (created on demand). */
    public PlayerTrace trace(Player player) {
        return traces.computeIfAbsent(player.getUniqueId(),
                uuid -> new PlayerTrace(uuid, player.getName(), System.currentTimeMillis()));
    }

    /** The live trace of a player, or {@code null}. */
    public PlayerTrace traceIfPresent(UUID uuid) {
        return traces.get(uuid);
    }

    /** All live traces, for the /sus GUI. */
    public List<PlayerTrace> traces() {
        return List.copyOf(traces.values());
    }

    // ------------------------------------------------------------- flagging

    /**
     * Records a detection: raises the check's violation level, stores a flag,
     * alerts staff and escalates to kick / automatic ban at the configured
     * thresholds.
     */
    public void flag(Player player, CheckType check, double vlAmount, String detail) {
        if (!config.anticheatEnabled() || player.hasPermission("veltryx.anticheat.bypass")) {
            return;
        }
        PlayerTrace trace = trace(player);
        long now = System.currentTimeMillis();
        if (now - trace.joinedAtMillis() < config.joinGraceSeconds() * 1000L) {
            return;
        }
        int ping = player.getPing();
        double tps = currentTps();
        if (PingGuard.shouldSkip(ping, tps, config.maxPing(), config.minTps())) {
            return;
        }

        CheckType.Plan plan = plan(check);
        if (!plan.enabled()) {
            return;
        }

        double newVl = trace.addViolation(check, vlAmount);
        trace.addFlag(check, newVl, detail, now);
        store.saveFlag(new FlagEntry(Ids.next(), player.getUniqueId(), player.getName(),
                check.display(), newVl, detail, now));

        if (newVl >= plan.alertVl() && now - trace.lastAlertMillis(check) >= 5_000L) {
            trace.markAlerted(check, now);
            alert(player, check, newVl, detail, ping);
        }

        boolean reliable = PingGuard.reliable(ping, tps, config.punishMaxPing(), config.punishMinTps());
        if (!reliable) {
            return;
        }

        if (newVl >= plan.banVl()) {
            autoBan(player, check, plan);
        } else if (newVl >= plan.kickVl()) {
            autoKick(player, check, plan);
        }
    }

    /** Sends a staff alert for a flagged player. */
    private void alert(Player player, CheckType check, double vl, String detail, int ping) {
        Component message = messages.render("anticheat.alert", Map.of(
                "player", player.getName(),
                "check", check.display(),
                "vl", String.format(java.util.Locale.ROOT, "%.1f", vl),
                "detail", detail,
                "ping", String.valueOf(ping)));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("veltryx.anticheat.alerts")) {
                staff.sendMessage(message);
            }
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    private void autoKick(Player player, CheckType check, CheckType.Plan plan) {
        player.kick(messages.render("anticheat.kick", Map.of(
                "check", check.display(),
                "reason", plan.banReason())));
        notifyPunishment(player.getName(), check, "kicked", "0s");
    }

    private void autoBan(Player player, CheckType check, CheckType.Plan plan) {
        PlayerTrace trace = trace(player);
        String evidence = evidenceSummary(trace, check);
        bans.ban(player.getUniqueId(), player.getName(),
                "auto-" + check.name().toLowerCase(java.util.Locale.ROOT),
                plan.banReason(), evidence, "Veltryx AntiCheat",
                TimeUtil.expiryMillis(System.currentTimeMillis(), plan.banSeconds()), true);
        notifyPunishment(player.getName(), check,
                plan.banSeconds() == Long.MAX_VALUE ? "permanently banned"
                        : "banned for " + TimeUtil.format(plan.banSeconds()),
                plan.banSeconds() == Long.MAX_VALUE ? "perm" : TimeUtil.format(plan.banSeconds()));
    }

    private void notifyPunishment(String name, CheckType check, String action, String duration) {
        Component message = messages.render("anticheat.punished", Map.of(
                "player", name,
                "check", check.display(),
                "action", action,
                "duration", duration));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("veltryx.anticheat.alerts")) {
                staff.sendMessage(message);
            }
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    /** Compact evidence string from the recent flags of a trace. */
    public static String evidenceSummary(PlayerTrace trace, CheckType check) {
        List<String> parts = new ArrayList<>();
        for (PlayerTrace.Flag flag : trace.recentFlags()) {
            if (flag.check() == check || parts.size() < 5) {
                parts.add(flag.check().display() + " " + String.format(java.util.Locale.ROOT, "%.1f", flag.vl())
                        + " (" + flag.detail() + ")");
            }
            if (parts.size() >= 8) {
                break;
            }
        }
        return String.join("; ", parts);
    }

    // ------------------------------------------------------------ suspicion

    /**
     * Suspicion score for /sus: total violation level plus recent flags.
     * Higher means more suspicious.
     */
    public double suspicion(PlayerTrace trace) {
        double score = trace.totalViolationLevel();
        long cutoff = System.currentTimeMillis() - 600_000L;
        for (PlayerTrace.Flag flag : trace.recentFlags()) {
            if (flag.issuedMillis() >= cutoff) {
                score += 2.0;
            }
        }
        return score;
    }

    /** Live traces sorted by suspicion, most suspicious first. */
    public List<PlayerTrace> ranked() {
        List<PlayerTrace> list = new ArrayList<>(traces.values());
        list.sort(Comparator.comparingDouble(this::suspicion).reversed());
        return list;
    }

    /** Flags recorded for a player in the persistent store, newest first. */
    public List<FlagEntry> storedFlags(UUID uuid, int limit) {
        List<FlagEntry> matching = store.flagsFor(uuid);
        return matching.size() > limit ? matching.subList(0, limit) : matching;
    }

    // -------------------------------------------------------------- timers

    /** Starts the global decay + prune timer. */
    public void startTimer() {
        PlatformScheduler.runTimer(() -> {
            long now = System.currentTimeMillis();
            long last = lastDecayMillis;
            lastDecayMillis = now;
            for (PlayerTrace trace : traces.values()) {
                trace.decayAll(config.decayPerSecond(), now, last);
            }
            if (now % 600_000L < 20_000L) {
                store.pruneFlags(now - config.flagRetentionSeconds() * 1000L);
            }
        }, 100L);
    }

    /** Current server TPS (first sample), or 20 when unavailable. */
    public static double currentTps() {
        try {
            double[] tps = Bukkit.getTPS();
            return tps == null || tps.length == 0 ? 20.0 : Math.min(20.0, tps[0]);
        } catch (Throwable t) {
            return 20.0;
        }
    }
}
