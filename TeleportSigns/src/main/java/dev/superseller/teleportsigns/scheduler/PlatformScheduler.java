package dev.superseller.teleportsigns.scheduler;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Folia-safe scheduling via Paper region APIs (reflection) with Bukkit fallback.
 */
public final class PlatformScheduler {

    private static JavaPlugin plugin;
    private static Logger logger;

    private static Method entitySchedulerGet;
    private static Method entityRun;
    private static Method entityRunDelayed;
    private static Method globalSchedulerGet;
    private static Method globalRun;
    private static Method regionSchedulerGet;
    private static Method regionExecute;
    private static Method regionRun;

    private PlatformScheduler() {
    }

    public static void init(JavaPlugin pl) {
        plugin = pl;
        logger = pl.getLogger();
        try {
            entitySchedulerGet = Player.class.getMethod("getScheduler");
            Class<?> entityScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            entityRun = entityScheduler.getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class, Runnable.class);
            entityRunDelayed = entityScheduler.getMethod("runDelayed", org.bukkit.plugin.Plugin.class,
                    Consumer.class, Runnable.class, long.class);
        } catch (Throwable ignored) {
            // older Paper / unexpected
        }
        try {
            globalSchedulerGet = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Class<?> globalScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            globalRun = globalScheduler.getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class);
        } catch (Throwable ignored) {
            // older Paper
        }
        try {
            regionSchedulerGet = Bukkit.class.getMethod("getRegionScheduler");
            Class<?> regionScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
            try {
                regionExecute = regionScheduler.getMethod("execute",
                        org.bukkit.plugin.Plugin.class, Location.class, Runnable.class);
            } catch (Throwable ignored) {
                regionExecute = null;
            }
            try {
                regionRun = regionScheduler.getMethod("run",
                        org.bukkit.plugin.Plugin.class, Location.class, Consumer.class);
            } catch (Throwable ignored) {
                regionRun = null;
            }
        } catch (Throwable ignored) {
            // older Paper
        }
    }

    public static void runEntity(Player player, Runnable task) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (entityRun != null) {
            try {
                Object scheduler = entitySchedulerGet.invoke(player);
                entityRun.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run(), (Runnable) null);
                return;
            } catch (Throwable e) {
                logger.warning("Entity scheduler failed: " + e.getMessage());
            }
        }
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (Throwable e) {
            logger.warning("Bukkit scheduler unavailable: " + e.getMessage());
        }
    }

    public static void runEntityDelayed(Player player, Runnable task, long delayTicks) {
        if (player == null || !player.isOnline()) {
            return;
        }
        long delay = Math.max(1L, delayTicks);
        if (entityRunDelayed != null) {
            try {
                Object scheduler = entitySchedulerGet.invoke(player);
                entityRunDelayed.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run(),
                        (Runnable) null, delay);
                return;
            } catch (Throwable e) {
                logger.warning("Entity delayed scheduler failed: " + e.getMessage());
            }
        }
        try {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        } catch (Throwable e) {
            logger.warning("Bukkit delayed scheduler unavailable: " + e.getMessage());
        }
    }

    public static void runGlobal(Runnable task) {
        if (globalRun != null) {
            try {
                Object scheduler = globalSchedulerGet.invoke(null);
                globalRun.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run());
                return;
            } catch (Throwable e) {
                logger.warning("Global scheduler failed: " + e.getMessage());
            }
        }
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (Throwable e) {
            logger.warning("Bukkit scheduler unavailable: " + e.getMessage());
        }
    }

    public static void runRegion(Location location, Runnable task) {
        if (location == null || location.getWorld() == null) {
            runGlobal(task);
            return;
        }
        if (regionSchedulerGet != null) {
            try {
                Object scheduler = regionSchedulerGet.invoke(null);
                if (regionExecute != null) {
                    regionExecute.invoke(scheduler, plugin, location, task);
                    return;
                }
                if (regionRun != null) {
                    regionRun.invoke(scheduler, plugin, location, (Consumer<Object>) t -> task.run());
                    return;
                }
            } catch (Throwable e) {
                logger.warning("Region scheduler failed: " + e.getMessage());
            }
        }
        runGlobal(task);
    }
}
