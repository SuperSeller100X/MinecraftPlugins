package dev.superseller.playerbank.interest;

import dev.superseller.playerbank.PlayerBankPlugin;
import dev.superseller.playerbank.config.BankConfig;
import dev.superseller.playerbank.model.BankAccount;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Periodic compound interest. Folia-safe: the repeating task runs on the
 * global region scheduler and only touches plugin data (accounts are
 * synchronized) — never world or entity state. Player notifications are
 * hopped onto the owning player's region thread via the entity scheduler.
 */
public final class InterestService {

    private final PlayerBankPlugin plugin;
    private ScheduledTask task;
    private volatile long nextRunAt;

    public InterestService(PlayerBankPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        BankConfig cfg = plugin.bankConfig();
        if (!cfg.interestEnabled()) {
            plugin.getLogger().info("Interest is disabled.");
            return;
        }
        long ticks = cfg.intervalTicks();
        nextRunAt = System.currentTimeMillis() + cfg.intervalMillis();
        task = plugin.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(plugin, timer -> tick(), ticks, ticks);
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

    public long nextRunAt() {
        return nextRunAt;
    }

    public String nextRunHuman() {
        long remaining = Math.max(0, nextRunAt - System.currentTimeMillis());
        long totalSec = remaining / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) {
            return h + "h " + m + "m " + s + "s";
        }
        if (m > 0) {
            return m + "m " + s + "s";
        }
        return s + "s";
    }

    /**
     * Applies one interest pass. Runs on the global region thread from the
     * timer, or on the caller's thread when an admin forces it — both are
     * safe because every account access is synchronized.
     */
    public int tick() {
        BankConfig cfg = plugin.bankConfig();
        nextRunAt = System.currentTimeMillis() + cfg.intervalMillis();
        if (!cfg.interestEnabled()) {
            return 0;
        }
        double rate = cfg.ratePercent() / 100.0;
        if (rate <= 0) {
            return 0;
        }
        int updated = 0;
        for (BankAccount acc : plugin.storage().all()) {
            if (acc.balance() < cfg.minBalance()) {
                continue;
            }
            double payout = cfg.roundMoney(acc.balance() * rate);
            if (payout <= 0) {
                continue;
            }
            double next = acc.balance() + payout;
            if (cfg.maxBalance() > 0 && next > cfg.maxBalance()) {
                payout = cfg.roundMoney(Math.max(0, cfg.maxBalance() - acc.balance()));
                next = acc.balance() + payout;
            }
            if (payout <= 0) {
                continue;
            }
            acc.balance(next);
            plugin.storage().log(acc, "INTEREST", payout, cfg.ratePercent() + "% compound");
            updated++;
            if (cfg.notifyPlayers()) {
                Player player = Bukkit.getPlayer(acc.uuid());
                if (player != null && player.isOnline()) {
                    double payoutFinal = payout;
                    double balanceFinal = acc.balance();
                    String rateText = String.valueOf(cfg.ratePercent());
                    // Hop to the player's owning region thread before touching
                    // the economy formatter or sending chat.
                    player.getScheduler().run(plugin, task -> plugin.messages().send(player,
                            "interest-received", Map.of(
                                    "amount", plugin.vault().format(payoutFinal),
                                    "rate", rateText,
                                    "bank", plugin.vault().format(balanceFinal))), null);
                }
            }
        }
        if (updated > 0) {
            plugin.storage().markDirty();
            plugin.getLogger().info("Applied compound interest to " + updated + " accounts.");
        }
        return updated;
    }
}
