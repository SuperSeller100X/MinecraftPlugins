package dev.superseller.hourglass.scheduler;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Cross-platform task scheduling with no compile-time dependency on the
 * Folia-only scheduler classes. Uses Paper's entity / global region schedulers
 * through reflection when they exist and falls back to the classic Bukkit
 * scheduler otherwise, so a single jar is correct on Paper, Purpur and Folia.
 *
 * <p>Conventions used by this plugin:
 * <ul>
 *   <li>Anything that touches a live {@link Player} (GUI, boss bar, sound,
 *       title) runs on that player's owning region thread — mandatory on Folia.</li>
 *   <li>The accumulation and leaderboard timers run on the global region thread
 *       and only touch this plugin's own maps, never world or entity state.</li>
 *   <li>File I/O runs on the plugin's own writer thread in
 *       {@code YamlPlayerStorage}, which is cheaper than borrowing async
 *       scheduler threads for blocking disk calls.</li>
 * </ul>
 */
public final class PlatformScheduler {

    private static JavaPlugin plugin;
    private static Logger logger;

    private static Method entitySchedulerGet;
    private static Method entityRun;
    private static Method entityRunDelayed;
    private static Method globalSchedulerGet;
    private static Method globalRun;
    private static Method globalRunDelayed;
    private static Method globalRunAtFixedRate;
    private static boolean folia;

    private PlatformScheduler() {
    }

    /** Must be called first thing in {@code onEnable}. */
    public static void init(JavaPlugin plugin) {
        PlatformScheduler.plugin = plugin;
        logger = plugin.getLogger();
        try {
            entitySchedulerGet = Player.class.getMethod("getScheduler");
            Class<?> entityScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            entityRun = entityScheduler.getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class, Runnable.class);
            entityRunDelayed = entityScheduler.getMethod("runDelayed", org.bukkit.plugin.Plugin.class,
                    Consumer.class, long.class, Runnable.class);
        } catch (Throwable ignored) {
            entityRun = null;
            entityRunDelayed = null;
        }
        try {
            globalSchedulerGet = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Class<?> globalScheduler =
                    Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            globalRun = globalScheduler.getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class);
            globalRunDelayed = globalScheduler.getMethod("runDelayed", org.bukkit.plugin.Plugin.class,
                    Consumer.class, long.class);
            globalRunAtFixedRate = globalScheduler.getMethod("runAtFixedRate", org.bukkit.plugin.Plugin.class,
                    Consumer.class, long.class, long.class);
            folia = true;
        } catch (Throwable ignored) {
            globalRun = null;
        }
    }

    /**
     * {@code true} when the region schedulers are present (Folia, or Paper in
     * regionised mode). Purely informational — every call works either way.
     */
    public static boolean isFolia() {
        return folia;
    }

    /** Runs a task on the given player's owning region thread on the next tick. */
    public static void runForPlayer(Player player, Runnable task) {
        if (player == null) {
            return;
        }
        if (entityRun != null) {
            try {
                Object scheduler = entitySchedulerGet.invoke(player);
                entityRun.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run(), (Runnable) null);
                return;
            } catch (Throwable e) {
                warn("Entity scheduler failed, using Bukkit scheduler", e);
            }
        }
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (Throwable e) {
            warn("Bukkit scheduler unavailable", e);
        }
    }

    /** Runs a task on the player's region thread after {@code delayTicks}. */
    public static void runForPlayerLater(Player player, long delayTicks, Runnable task) {
        if (player == null) {
            return;
        }
        long delay = Math.max(1L, delayTicks);
        if (entityRunDelayed != null) {
            try {
                Object scheduler = entitySchedulerGet.invoke(player);
                entityRunDelayed.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run(), delay,
                        (Runnable) null);
                return;
            } catch (Throwable e) {
                warn("Entity scheduler (delayed) failed, using Bukkit scheduler", e);
            }
        }
        try {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        } catch (Throwable e) {
            warn("Bukkit scheduler unavailable", e);
        }
    }

    /** Runs a task on the main / global region thread on the next tick. */
    public static void runGlobal(Runnable task) {
        if (globalRun != null) {
            try {
                Object scheduler = globalSchedulerGet.invoke(null);
                globalRun.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run());
                return;
            } catch (Throwable e) {
                warn("Global scheduler failed, using Bukkit scheduler", e);
            }
        }
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (Throwable e) {
            warn("Bukkit scheduler unavailable", e);
        }
    }

    /** A handle to a repeating task that can be cancelled. */
    @FunctionalInterface
    public interface TaskHandle {
        void cancel();

        /** A handle that does nothing. */
        TaskHandle NONE = () -> {
        };
    }

    /**
     * Starts a repeating task on the global region thread (main thread on
     * Paper) and returns a cancellation handle.
     */
    public static TaskHandle runTimer(Runnable task, long delayTicks, long periodTicks) {
        long delay = Math.max(0L, delayTicks);
        long period = Math.max(1L, periodTicks);
        if (globalRunAtFixedRate != null) {
            try {
                Object scheduler = globalSchedulerGet.invoke(null);
                Object scheduled = globalRunAtFixedRate.invoke(scheduler, plugin,
                        (Consumer<Object>) t -> task.run(), delay, period);
                if (scheduled != null) {
                    return cancellerOf(scheduled);
                }
            } catch (Throwable e) {
                warn("Global timer failed, using Bukkit scheduler", e);
            }
        }
        try {
            org.bukkit.scheduler.BukkitTask bukkitTask =
                    Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
            return () -> {
                try {
                    bukkitTask.cancel();
                } catch (Throwable e) {
                    warn("Failed to cancel Bukkit timer", e);
                }
            };
        } catch (Throwable e) {
            warn("Bukkit scheduler unavailable", e);
            return TaskHandle.NONE;
        }
    }

    /** Runs a task on the main / global region thread after {@code delayTicks}. */
    public static void runGlobalLater(long delayTicks, Runnable task) {
        long delay = Math.max(1L, delayTicks);
        if (globalRunDelayed != null) {
            try {
                Object scheduler = globalSchedulerGet.invoke(null);
                globalRunDelayed.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run(), delay);
                return;
            } catch (Throwable e) {
                warn("Global scheduler (delayed) failed, using Bukkit scheduler", e);
            }
        }
        try {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        } catch (Throwable e) {
            warn("Bukkit scheduler unavailable", e);
        }
    }

    /** Runs a task off the main thread (used for the shutdown flush only). */
    public static void runAsync(Runnable task) {
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            return;
        } catch (Throwable e) {
            // Either Folia (no Bukkit async scheduler) or a disabled plugin.
        }
        try {
            Object scheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            Class<?> asyncScheduler =
                    Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            Method runNow = asyncScheduler.getMethod("runNow", org.bukkit.plugin.Plugin.class, Consumer.class);
            runNow.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run());
        } catch (Throwable e) {
            warn("No async scheduler available, running inline", e);
            task.run();
        }
    }

    private static TaskHandle cancellerOf(Object scheduledTask) {
        return () -> {
            try {
                Method cancel = scheduledTask.getClass().getMethod("cancel");
                cancel.setAccessible(true);
                cancel.invoke(scheduledTask);
            } catch (Throwable e) {
                warn("Failed to cancel scheduled task", e);
            }
        };
    }

    private static void warn(String what, Throwable e) {
        if (logger != null) {
            logger.warning(what + ": " + e.getMessage());
        }
    }
}
