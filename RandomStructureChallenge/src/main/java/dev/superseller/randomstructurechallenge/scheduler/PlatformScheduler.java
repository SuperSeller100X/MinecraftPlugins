package dev.superseller.randomstructurechallenge.scheduler;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Cross-platform task scheduling with zero compile-time Folia types.
 * Uses Paper's entity / global / region schedulers when present, otherwise
 * the Bukkit scheduler. Safe on Linux, Windows and macOS.
 */
public final class PlatformScheduler {

    private static JavaPlugin plugin;
    private static Logger logger;

    private static Method entitySchedulerGet;
    private static Method entityRun;
    private static Method globalSchedulerGet;
    private static Method globalRun;
    private static Method globalRunAtFixedRate;
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
        } catch (Throwable ignored) {
            // older Paper / non-Folia
        }
        try {
            globalSchedulerGet = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Class<?> globalScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            globalRun = globalScheduler.getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class);
            globalRunAtFixedRate = globalScheduler.getMethod(
                    "runAtFixedRate", org.bukkit.plugin.Plugin.class, Consumer.class, long.class, long.class);
        } catch (Throwable ignored) {
            // plain Bukkit scheduler fallback
        }
        try {
            regionSchedulerGet = Bukkit.class.getMethod("getRegionScheduler");
            Class<?> regionScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
            try {
                regionExecute = regionScheduler.getMethod(
                        "execute", org.bukkit.plugin.Plugin.class, Location.class, Runnable.class);
            } catch (Throwable ignored) {
                regionExecute = null;
            }
            try {
                regionRun = regionScheduler.getMethod(
                        "run", org.bukkit.plugin.Plugin.class, Location.class, Consumer.class);
            } catch (Throwable ignored) {
                regionRun = null;
            }
        } catch (Throwable ignored) {
            // no region scheduler
        }
    }

    public static void runEntitySync(Player player, Runnable task) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (entityRun != null) {
            try {
                Object scheduler = entitySchedulerGet.invoke(player);
                entityRun.invoke(scheduler, plugin, (Consumer<Object>) taskUnused -> task.run(), (Runnable) null);
                return;
            } catch (Throwable e) {
                logger.warning("Entity scheduler failed, falling back to Bukkit: " + e.getMessage());
            }
        }
        runBukkit(task);
    }

    public static void runGlobal(Runnable task) {
        if (globalRun != null) {
            try {
                Object scheduler = globalSchedulerGet.invoke(null);
                globalRun.invoke(scheduler, plugin, (Consumer<Object>) taskUnused -> task.run());
                return;
            } catch (Throwable e) {
                logger.warning("Global scheduler failed, falling back to Bukkit: " + e.getMessage());
            }
        }
        runBukkit(task);
    }

    public static void runTimer(Runnable task, long periodTicks) {
        if (globalRunAtFixedRate != null) {
            try {
                Object scheduler = globalSchedulerGet.invoke(null);
                globalRunAtFixedRate.invoke(
                        scheduler, plugin, (Consumer<Object>) taskUnused -> task.run(), 1L, periodTicks);
                return;
            } catch (Throwable e) {
                logger.warning("Global timer failed, falling back to Bukkit: " + e.getMessage());
            }
        }
        try {
            Bukkit.getScheduler().runTaskTimer(plugin, task, 1L, periodTicks);
        } catch (Throwable e) {
            logger.warning("Bukkit scheduler unavailable: " + e.getMessage());
        }
    }

    public static void runRegionSync(World world, Location location, Runnable task) {
        if (world == null || location == null) {
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
                    regionRun.invoke(scheduler, plugin, location, (Consumer<Object>) taskUnused -> task.run());
                    return;
                }
            } catch (Throwable e) {
                logger.warning("Region scheduler failed, falling back to global: " + e.getMessage());
            }
        }
        runGlobal(task);
    }

    private static void runBukkit(Runnable task) {
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (Throwable e) {
            logger.warning("Bukkit scheduler unavailable: " + e.getMessage());
        }
    }
}
