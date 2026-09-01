package dev.superseller.xpbank.bank;

import dev.superseller.xpbank.XPBankPlugin;
import dev.superseller.xpbank.config.XPBankConfig;
import dev.superseller.xpbank.scheduler.PlatformScheduler;
import dev.superseller.xpbank.storage.BankStorage;
import dev.superseller.xpbank.util.Numbers;
import dev.superseller.xpbank.util.Sounds;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Pays configurable interest on banked XP, like a savings account.
 *
 * <p>Disabled by default. When enabled, every interval each account that meets
 * the minimum balance earns {@code rate%} of its banked XP (compounded, since it
 * is added back to the balance). Interest only ever touches stored balances — it
 * never reads or writes a player's live XP bar — so it is safe to run from the
 * global region thread on Paper, Purpur and Folia, and works for offline players.
 */
public final class InterestService {

    private final XPBankPlugin plugin;
    private PlatformScheduler.Cancellable task;
    private long nextRunAt;

    public InterestService(XPBankPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        XPBankConfig cfg = plugin.bankConfig();
        if (!cfg.interestEnabled()) {
            plugin.getLogger().info("XP interest is disabled.");
            return;
        }
        long ticks = cfg.interestIntervalTicks();
        nextRunAt = System.currentTimeMillis() + cfg.interestIntervalMillis();
        task = PlatformScheduler.runTimerCancellable(this::tick, ticks);
        plugin.getLogger().info("XP interest enabled: " + cfg.interestRatePercent()
                + "% every " + cfg.interestIntervalDescription() + ".");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void restart() {
        start();
    }

    public boolean isRunning() {
        return task != null;
    }

    public long nextRunAt() {
        return nextRunAt;
    }

    /**
     * Applies one round of interest to every qualifying account. Returns the
     * number of accounts that were paid. Package-visible so it can be triggered
     * by an admin command.
     */
    public int tick() {
        XPBankConfig cfg = plugin.bankConfig();
        nextRunAt = System.currentTimeMillis() + cfg.interestIntervalMillis();
        if (!cfg.interestEnabled()) {
            return 0;
        }
        double rate = cfg.interestRatePercent() / 100.0;
        if (rate <= 0) {
            return 0;
        }

        BankStorage storage = plugin.storage();
        Map<UUID, Long> accounts = storage.all();
        int paid = 0;

        for (Map.Entry<UUID, Long> entry : accounts.entrySet()) {
            UUID uuid = entry.getKey();
            long balance = entry.getValue();
            if (balance < cfg.interestMinBalance()) {
                continue;
            }
            if (!cfg.interestOfflinePlayers() && Bukkit.getPlayer(uuid) == null) {
                continue;
            }

            long payout = (long) Math.floor(balance * rate);
            if (cfg.interestMaxPayout() > 0) {
                payout = Math.min(payout, cfg.interestMaxPayout());
            }
            if (payout <= 0) {
                continue;
            }

            long updated = balance + payout;
            storage.set(uuid, storage.nameOf(uuid), updated);
            paid++;

            if (cfg.interestNotify()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    long finalPayout = payout;
                    long finalBalance = updated;
                    PlatformScheduler.runForPlayer(player, () -> {
                        plugin.messages().send(player, "interest-received", Map.of(
                                "amount", Numbers.grouped(finalPayout),
                                "amount_short", Numbers.compact(finalPayout),
                                "rate", trimRate(cfg.interestRatePercent()),
                                "banked", Numbers.grouped(finalBalance)));
                        Sounds.play(cfg, player, "interest");
                    });
                }
            }
        }

        if (paid > 0) {
            PlatformScheduler.runAsync(() -> plugin.storage().save());
            plugin.getLogger().info("Applied XP interest to " + paid + " account(s).");
        }
        return paid;
    }

    private static String trimRate(double rate) {
        if (rate == Math.floor(rate)) {
            return String.valueOf((long) rate);
        }
        return String.valueOf(rate);
    }
}
