package dev.superseller.subscriptions.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Folia-safe scheduling for Paper / Purpur / Folia 26.2.
 *
 * <ul>
 *   <li>Global tasks — shared data, files, billing, commands.</li>
 *   <li>Entity tasks — opening GUIs, talking to a player, inventory.</li>
 * </ul>
 *
 * Falls back to the Bukkit scheduler when region schedulers are missing
 * (should not happen on 26.2, but keeps tests and odd forks alive).
 */
public final class PlatformScheduler {

    private final JavaPlugin plugin;
    private final boolean folia;

    public PlatformScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
        boolean detected = false;
        try {
            plugin.getServer().getGlobalRegionScheduler();
            detected = true;
        } catch (Throwable ignored) {
            detected = false;
        }
        this.folia = detected;
    }

    public boolean folia() {
        return folia;
    }

    public void runGlobal(Runnable task) {
        if (folia) {
            plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> task.run());
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public void runGlobalLater(Runnable task, long delayTicks) {
        long delay = Math.max(1L, delayTicks);
        if (folia) {
            plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), delay);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }

    public void runTimer(Runnable task, long periodTicks) {
        long period = Math.max(1L, periodTicks);
        if (folia) {
            plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), 1L, period);
            return;
        }
        Bukkit.getScheduler().runTaskTimer(plugin, task, 1L, period);
    }

    public void runAsync(Runnable task) {
        if (folia) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, t -> task.run());
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public void runEntity(Player player, Runnable task) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (folia) {
            player.getScheduler().run(plugin, t -> task.run(), null);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }
}
