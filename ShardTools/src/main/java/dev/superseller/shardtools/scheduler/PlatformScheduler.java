package dev.superseller.shardtools.scheduler;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Folia-safe scheduling. Uses Paper's regionized schedulers through
 * reflection when present (Folia), and falls back to the classic Bukkit
 * scheduler on Paper / Purpur. Works on Linux, Windows and macOS because
 * it only depends on JVM APIs.
 */
public final class PlatformScheduler {

    private static JavaPlugin plugin;
    private static Logger logger;

    private static Method entitySchedulerGet;
    private static Method entityRun;
    private static Method entityRunDelayed;
    private static Method globalSchedulerGet;
    private static Method globalRun;
    private static Method globalRunAtFixedRate;
    private static Method asyncSchedulerGet;
    private static Method asyncRunNow;
    private static Method globalCancelTasks;
    private static Method asyncCancelTasks;

    private PlatformScheduler() {
    }

    public static void init(JavaPlugin owner) {
        plugin = owner;
        logger = owner.getLogger();
        try {
            entitySchedulerGet = Player.class.getMethod("getScheduler");
            Class<?> entityScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            entityRun = entityScheduler.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            entityRunDelayed = entityScheduler.getMethod("runDelayed", Plugin.class,
                    Consumer.class, Runnable.class, long.class);
        } catch (Throwable ignored) {
            // classic Paper / Purpur
        }
        try {
            globalSchedulerGet = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Class<?> globalScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            globalRun = globalScheduler.getMethod("run", Plugin.class, Consumer.class);
            globalRunAtFixedRate = globalScheduler.getMethod("runAtFixedRate", Plugin.class,
                    Consumer.class, long.class, long.class);
            globalCancelTasks = globalScheduler.getMethod("cancelTasks", Plugin.class);
        } catch (Throwable ignored) {
            // classic Paper / Purpur
        }
        try {
            asyncSchedulerGet = Bukkit.class.getMethod("getAsyncScheduler");
            Class<?> asyncScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            asyncRunNow = asyncScheduler.getMethod("runNow", Plugin.class, Consumer.class);
            asyncCancelTasks = asyncScheduler.getMethod("cancelTasks", Plugin.class);
        } catch (Throwable ignored) {
            // classic Paper / Purpur
        }
        logger.info("Scheduling mode: " + (isFolia() ? "Folia (regionized)" : "Paper/Purpur (Bukkit scheduler)"));
    }

    public static boolean isFolia() {
        if (globalRunAtFixedRate == null) {
            return false;
        }
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Runs on the entity's region thread (inventory/chat interactions). */
    public static void runEntity(Player player, Runnable task) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (entityRun != null && isFolia()) {
            try {
                Object scheduler = entitySchedulerGet.invoke(player);
                entityRun.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run(), (Runnable) null);
                return;
            } catch (Throwable error) {
                logger.warning("Entity scheduler failed: " + error.getMessage());
            }
        }
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (Throwable error) {
            logger.warning("Bukkit scheduler unavailable: " + error.getMessage());
        }
    }

    /** Like {@link #runEntity} but after a delay in ticks. */
    public static void runEntityLater(Player player, Runnable task, long delayTicks) {
        if (player == null || !player.isOnline()) {
            return;
        }
        long delay = Math.max(1L, delayTicks);
        if (entityRunDelayed != null && isFolia()) {
            try {
                Object scheduler = entitySchedulerGet.invoke(player);
                entityRunDelayed.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run(),
                        (Runnable) null, delay);
                return;
            } catch (Throwable error) {
                logger.warning("Entity delayed scheduler failed: " + error.getMessage());
            }
        }
        try {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        } catch (Throwable error) {
            logger.warning("Bukkit scheduler unavailable: " + error.getMessage());
        }
    }

    /** Runs on the global region (Folia) or the main thread (Paper). */
    public static void runGlobal(Runnable task) {
        if (globalRun != null && isFolia()) {
            try {
                Object scheduler = globalSchedulerGet.invoke(null);
                globalRun.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run());
                return;
            } catch (Throwable error) {
                logger.warning("Global scheduler failed: " + error.getMessage());
            }
        }
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (Throwable error) {
            logger.warning("Bukkit scheduler unavailable: " + error.getMessage());
        }
    }

    /** Repeating global task (award income, expiry sweep). */
    public static BukkitTask runRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
        long period = Math.max(1L, periodTicks);
        if (globalRunAtFixedRate != null && isFolia()) {
            try {
                Object scheduler = globalSchedulerGet.invoke(null);
                globalRunAtFixedRate.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run(),
                        Math.max(1L, initialDelayTicks), period);
                return null;
            } catch (Throwable error) {
                logger.warning("Global repeating scheduler failed: " + error.getMessage());
            }
        }
        return Bukkit.getScheduler().runTaskTimer(plugin, task, Math.max(1L, initialDelayTicks), period);
    }

    /** Off-thread work (file IO). */
    public static void runAsync(Runnable task) {
        if (asyncRunNow != null && isFolia()) {
            try {
                Object scheduler = asyncSchedulerGet.invoke(null);
                asyncRunNow.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run());
                return;
            } catch (Throwable error) {
                logger.warning("Async scheduler failed: " + error.getMessage());
            }
        }
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        } catch (Throwable error) {
            logger.warning("Bukkit async scheduler unavailable: " + error.getMessage());
        }
    }

    /** Cancels every task this plugin scheduled. */
    public static void cancelAll() {
        try {
            if (globalCancelTasks != null && isFolia()) {
                globalCancelTasks.invoke(globalSchedulerGet.invoke(null), plugin);
            }
            if (asyncCancelTasks != null && isFolia()) {
                asyncCancelTasks.invoke(asyncSchedulerGet.invoke(null), plugin);
            }
        } catch (Throwable error) {
            logger.warning("Cancel via regionized schedulers failed: " + error.getMessage());
        }
        try {
            Bukkit.getScheduler().cancelTasks(plugin);
        } catch (Throwable ignored) {
            // Folia without Bukkit scheduler support
        }
    }

    /** Exposed for completeness (unused region helper kept for symmetry). */
    public static void runRegion(Location location, Runnable task) {
        if (location == null) {
            runGlobal(task);
            return;
        }
        runGlobal(task);
    }
}
