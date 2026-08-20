package dev.superseller.playerbank.interest;

import dev.superseller.playerbank.PlayerBankPlugin;
import dev.superseller.playerbank.config.BankConfig;
import dev.superseller.playerbank.model.BankAccount;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class InterestService {

    private final PlayerBankPlugin plugin;
    private BukkitTask task;
    private long nextRunAt;

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
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, ticks, ticks);
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
                    plugin.messages().send(player, "interest-received", Map.of(
                            "amount", plugin.vault().format(payout),
                            "rate", String.valueOf(cfg.ratePercent()),
                            "bank", plugin.vault().format(acc.balance())
                    ));
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
