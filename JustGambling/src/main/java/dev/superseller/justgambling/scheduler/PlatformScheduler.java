package dev.superseller.justgambling.scheduler;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

/**
 * Folia-aware scheduling with a Bukkit fallback for ordinary Paper/Purpur.
 * Reflection avoids linking the plugin to Folia-only classes at class load.
 */
public final class PlatformScheduler {
    private static JavaPlugin plugin;
    private static Logger logger;
    private static Method entityScheduler;
    private static Method entityRun;
    private static Method entityRunDelayed;
    private static Method globalScheduler;
    private static Method globalRun;
    private static Method globalRunDelayed;
    private static Method globalRunAtFixedRate;
    private static boolean foliaSchedulers;

    private PlatformScheduler() {
    }

    public static void init(JavaPlugin javaPlugin) {
        plugin = javaPlugin;
        logger = javaPlugin.getLogger();
        foliaSchedulers = false;
        try {
            entityScheduler = Player.class.getMethod("getScheduler");
            Class<?> type = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            entityRun = type.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            entityRunDelayed = type.getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
            foliaSchedulers = true;
        } catch (Throwable ignored) {
            entityScheduler = null;
            entityRun = null;
            entityRunDelayed = null;
        }
        try {
            globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Class<?> type = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            globalRun = type.getMethod("run", Plugin.class, Consumer.class);
            globalRunDelayed = type.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            globalRunAtFixedRate = type.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            foliaSchedulers = true;
        } catch (Throwable ignored) {
            globalScheduler = null;
            globalRun = null;
            globalRunDelayed = null;
            globalRunAtFixedRate = null;
        }
    }

    public static boolean isFoliaSchedulerAvailable() {
        return foliaSchedulers;
    }

    public static void runEntity(Player player, Runnable task) {
        if (player == null || !player.isOnline() || task == null) {
            return;
        }
        if (entityRun != null) {
            try {
                Object scheduler = entityScheduler.invoke(player);
                entityRun.invoke(scheduler, plugin, (Consumer<Object>) ignored -> task.run(), (Runnable) null);
                return;
            } catch (Throwable throwable) {
                logFailure("entity", throwable);
            }
        }
        fallback(() -> Bukkit.getScheduler().runTask(plugin, task));
    }

    public static void runEntityDelayed(Player player, Runnable task, long delayTicks) {
        if (player == null || !player.isOnline() || task == null) {
            return;
        }
        long delay = Math.max(1L, delayTicks);
        if (entityRunDelayed != null) {
            try {
                Object scheduler = entityScheduler.invoke(player);
                entityRunDelayed.invoke(scheduler, plugin, (Consumer<Object>) ignored -> task.run(), (Runnable) null, delay);
                return;
            } catch (Throwable throwable) {
                logFailure("delayed entity", throwable);
            }
        }
        fallback(() -> Bukkit.getScheduler().runTaskLater(plugin, task, delay));
    }

    public static void runGlobal(Runnable task) {
        if (task == null) {
            return;
        }
        if (globalRun != null) {
            try {
                Object scheduler = globalScheduler.invoke(null);
                globalRun.invoke(scheduler, plugin, (Consumer<Object>) ignored -> task.run());
                return;
            } catch (Throwable throwable) {
                logFailure("global", throwable);
            }
        }
        fallback(() -> Bukkit.getScheduler().runTask(plugin, task));
    }

    public static void runGlobalDelayed(Runnable task, long delayTicks) {
        if (task == null) {
            return;
        }
        long delay = Math.max(1L, delayTicks);
        if (globalRunDelayed != null) {
            try {
                Object scheduler = globalScheduler.invoke(null);
                globalRunDelayed.invoke(scheduler, plugin, (Consumer<Object>) ignored -> task.run(), delay);
                return;
            } catch (Throwable throwable) {
                logFailure("delayed global", throwable);
            }
        }
        fallback(() -> Bukkit.getScheduler().runTaskLater(plugin, task, delay));
    }

    public static void runGlobalRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
        if (task == null) {
            return;
        }
        long initial = Math.max(1L, initialDelayTicks);
        long period = Math.max(1L, periodTicks);
        if (globalRunAtFixedRate != null) {
            try {
                Object scheduler = globalScheduler.invoke(null);
                globalRunAtFixedRate.invoke(scheduler, plugin, (Consumer<Object>) ignored -> task.run(), initial, period);
                return;
            } catch (Throwable throwable) {
                logFailure("repeating global", throwable);
            }
        }
        fallback(() -> Bukkit.getScheduler().runTaskTimer(plugin, task, initial, period));
    }

    private static void fallback(Runnable invocation) {
        try {
            invocation.run();
        } catch (Throwable throwable) {
            logFailure("Bukkit fallback", throwable);
        }
    }

    private static void logFailure(String action, Throwable throwable) {
        if (logger != null) {
            logger.warning("JustGambling " + action + " scheduler failed: " + throwable.getMessage());
        }
    }
}
