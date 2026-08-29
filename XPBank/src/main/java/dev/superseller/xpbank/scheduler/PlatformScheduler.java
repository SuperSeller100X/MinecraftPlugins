package dev.superseller.xpbank.scheduler;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Cross-platform task scheduling with zero compile-time dependency on
 * Paper/Folia scheduler classes. Uses Paper's entity/global schedulers when
 * available (through reflection), otherwise falls back to the classic Bukkit
 * scheduler. This lets one jar run correctly on Paper, Purpur and Folia.
 *
 * <p>Conventions:
 * <ul>
 *   <li>Entity tasks run on the player's owning region thread — required on
 *       Folia before touching a player (XP, inventory, GUI).</li>
 *   <li>Global tasks / timers run on the global region thread and must only
 *       touch shared data or files, never live world/player state.</li>
 *   <li>Async tasks run off the main thread for disk / database I/O.</li>
 * </ul>
 */
public final class PlatformScheduler {

    private static JavaPlugin plugin;
    private static Logger logger;

    private static Method entitySchedulerGet;
    private static Method entityRun;
    private static Method globalSchedulerGet;
    private static Method globalRun;
    private static Method globalRunAtFixedRate;
    private static Method asyncSchedulerGet;
    private static Method asyncRunNow;

    private static boolean folia = false;

    private PlatformScheduler() {
    }

    public static void init(JavaPlugin pl) {
        plugin = pl;
        logger = pl.getLogger();
        try {
            entitySchedulerGet = Player.class.getMethod("getScheduler");
            Class<?> entityScheduler =
                    Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            entityRun = entityScheduler.getMethod("run",
                    org.bukkit.plugin.Plugin.class, Consumer.class, Runnable.class);
        } catch (Throwable ignored) {
            entityRun = null;
        }
        try {
            globalSchedulerGet = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Class<?> globalScheduler =
                    Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            globalRun = globalScheduler.getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class);
            globalRunAtFixedRate = globalScheduler.getMethod("runAtFixedRate",
                    org.bukkit.plugin.Plugin.class, Consumer.class, long.class, long.class);
            folia = true;
        } catch (Throwable ignored) {
            globalRun = null;
        }
        try {
            asyncSchedulerGet = Bukkit.class.getMethod("getAsyncScheduler");
            Class<?> asyncScheduler =
                    Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            asyncRunNow = asyncScheduler.getMethod("runNow", org.bukkit.plugin.Plugin.class, Consumer.class);
        } catch (Throwable ignored) {
            asyncRunNow = null;
        }
    }

    /** True when running on Folia (region schedulers present). */
    public static boolean isFolia() {
        return folia;
    }

    /** Runs a task on the given player's owning region thread on the next tick. */
    public static void runForPlayer(Player player, Runnable task) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (entityRun != null) {
            try {
                Object scheduler = entitySchedulerGet.invoke(player);
                entityRun.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run(), (Runnable) null);
                return;
            } catch (Throwable e) {
                logger.warning("Entity scheduler failed, using Bukkit scheduler: " + e.getMessage());
            }
        }
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (Throwable e) {
            logger.warning("Bukkit scheduler unavailable: " + e.getMessage());
        }
    }

    /** Runs a task on the main/global thread on the next tick. */
    public static void runGlobal(Runnable task) {
        if (globalRun != null) {
            try {
                Object scheduler = globalSchedulerGet.invoke(null);
                globalRun.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run());
                return;
            } catch (Throwable e) {
                logger.warning("Global scheduler failed, using Bukkit scheduler: " + e.getMessage());
            }
        }
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (Throwable e) {
            logger.warning("Bukkit scheduler unavailable: " + e.getMessage());
        }
    }

    /** Starts a repeating task safe to run from the global thread. */
    public static void runTimer(Runnable task, long periodTicks) {
        if (globalRunAtFixedRate != null) {
            try {
                Object scheduler = globalSchedulerGet.invoke(null);
                globalRunAtFixedRate.invoke(scheduler, plugin,
                        (Consumer<Object>) t -> task.run(), Math.max(1L, periodTicks), Math.max(1L, periodTicks));
                return;
            } catch (Throwable e) {
                logger.warning("Global timer failed, using Bukkit scheduler: " + e.getMessage());
            }
        }
        try {
            Bukkit.getScheduler().runTaskTimer(plugin, task, Math.max(1L, periodTicks), Math.max(1L, periodTicks));
        } catch (Throwable e) {
            logger.warning("Bukkit scheduler unavailable: " + e.getMessage());
        }
    }

    /** A handle to a running repeating task that can be cancelled. */
    @FunctionalInterface
    public interface Cancellable {
        void cancel();
    }

    /**
     * Starts a repeating task safe to run from the global thread and returns a
     * handle that cancels it. Works on Paper, Purpur and Folia.
     */
    public static Cancellable runTimerCancellable(Runnable task, long periodTicks) {
        long period = Math.max(1L, periodTicks);
        if (globalRunAtFixedRate != null) {
            try {
                Object scheduler = globalSchedulerGet.invoke(null);
                Object scheduledTask = globalRunAtFixedRate.invoke(scheduler, plugin,
                        (Consumer<Object>) t -> task.run(), period, period);
                if (scheduledTask != null) {
                    Method cancel = scheduledTask.getClass().getMethod("cancel");
                    cancel.setAccessible(true);
                    return () -> {
                        try {
                            cancel.invoke(scheduledTask);
                        } catch (Throwable e) {
                            logger.warning("Failed to cancel Folia task: " + e.getMessage());
                        }
                    };
                }
            } catch (Throwable e) {
                logger.warning("Global timer failed, using Bukkit scheduler: " + e.getMessage());
            }
        }
        try {
            org.bukkit.scheduler.BukkitTask bukkitTask =
                    Bukkit.getScheduler().runTaskTimer(plugin, task, period, period);
            return bukkitTask::cancel;
        } catch (Throwable e) {
            logger.warning("Bukkit scheduler unavailable: " + e.getMessage());
            return () -> {
            };
        }
    }

    /** Runs a task off the main thread (disk / database I/O). */
    public static void runAsync(Runnable task) {
        if (asyncRunNow != null) {
            try {
                Object scheduler = asyncSchedulerGet.invoke(null);
                asyncRunNow.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run());
                return;
            } catch (Throwable e) {
                logger.warning("Async scheduler failed, using Bukkit scheduler: " + e.getMessage());
            }
        }
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        } catch (Throwable e) {
            // Last resort: run inline so data is never silently lost.
            task.run();
        }
    }
}
