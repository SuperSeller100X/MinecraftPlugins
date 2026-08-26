package dev.superseller.playervault.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Folia-safe scheduling for Paper / Purpur / Folia 26.2.
 *
 * <ul>
 *   <li>Global tasks — shared data, files, timers, admin commands.</li>
 *   <li>Entity tasks — opening a GUI, messaging a player, editing their inventory.</li>
 *   <li>Async tasks — disk and database access.</li>
 * </ul>
 *
 * <p>Paper implements the region and async schedulers too, where they simply run on
 * the main thread, so the region APIs are used unconditionally. The legacy Bukkit
 * scheduler is only a fallback for forks that do not expose them.
 */
public final class PlatformScheduler {

    private final JavaPlugin plugin;
    private final boolean folia;

    public PlatformScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folia = detectFolia();
    }

    /**
     * {@code true} when running on Folia.
     *
     * <p>Detected by the regionised server marker class, as recommended by the Paper
     * documentation, rather than by probing for scheduler methods (which also exist
     * on plain Paper).
     */
    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    public boolean folia() {
        return folia;
    }

    /** Runs on the thread that owns shared server state. */
    public void runGlobal(Runnable task) {
        try {
            plugin.getServer().getGlobalRegionScheduler().run(plugin, scheduled -> task.run());
        } catch (Throwable ignored) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Runs on the global thread after {@code delayTicks}. */
    public void runGlobalLater(Runnable task, long delayTicks) {
        long delay = Math.max(1L, delayTicks);
        try {
            plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, scheduled -> task.run(), delay);
        } catch (Throwable ignored) {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }

    /** Repeats on the global thread. */
    public void runTimer(Runnable task, long periodTicks) {
        long period = Math.max(1L, periodTicks);
        try {
            plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, scheduled -> task.run(), 1L, period);
        } catch (Throwable ignored) {
            Bukkit.getScheduler().runTaskTimer(plugin, task, 1L, period);
        }
    }

    /** Runs off the region threads; never touch the world from here. */
    public void runAsync(Runnable task) {
        try {
            plugin.getServer().getAsyncScheduler().runNow(plugin, scheduled -> task.run());
        } catch (Throwable ignored) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /** Repeats off the region threads, for periodic disk writes. */
    public void runTimerAsync(Runnable task, long periodTicks) {
        long period = Math.max(1L, periodTicks);
        try {
            plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, scheduled -> task.run(),
                    period * 50L, period * 50L, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, period, period);
        }
    }

    /** Runs on the region thread that currently owns {@code player}. */
    public void runEntity(Player player, Runnable task) {
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            player.getScheduler().run(plugin, scheduled -> task.run(), null);
        } catch (Throwable ignored) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Runs on the player's region thread after {@code delayTicks}. */
    public void runEntityLater(Player player, Runnable task, long delayTicks) {
        if (player == null || !player.isOnline()) {
            return;
        }
        long delay = Math.max(1L, delayTicks);
        try {
            player.getScheduler().runDelayed(plugin, scheduled -> task.run(), null, delay);
        } catch (Throwable ignored) {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }

    /** Cancels every task this plugin scheduled. */
    public void cancel() {
        try {
            plugin.getServer().getGlobalRegionScheduler().cancelTasks(plugin);
        } catch (Throwable ignored) {
            Bukkit.getScheduler().cancelTasks(plugin);
        }
        try {
            plugin.getServer().getAsyncScheduler().cancelTasks(plugin);
        } catch (Throwable ignored) {
            // The async scheduler may not exist on this fork.
        }
    }
}
