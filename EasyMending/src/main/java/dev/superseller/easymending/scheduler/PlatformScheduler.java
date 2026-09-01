package dev.superseller.easymending.scheduler;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Folia-aware cross-platform task scheduler.
 * Utilizes Paper's entity/region schedulers when available, smoothly falling back
 * to standard Bukkit scheduling on Spigot or standard Paper.
 */
public final class PlatformScheduler {

    private static JavaPlugin plugin;
    private static Logger logger;

    private static Method entitySchedulerGet;
    private static Method entityRun;
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
        } catch (Throwable ignored) {
            // Bukkit / standard Paper
        }

        try {
            globalSchedulerGet = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Class<?> globalScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            globalRun = globalScheduler.getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class);
        } catch (Throwable ignored) {
            // Bukkit fallback
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
            // No region scheduler
        }
    }

    /**
     * Executes a task synchronously on the thread owned by the specific Player.
     * Essential on Folia to prevent cross-region thread violations.
     *
     * @param player player context
     * @param task task to run
     */
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

    /**
     * Executes a task globally on the server main thread or Folia global scheduler.
     *
     * @param task task to run
     */
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

    /**
     * Runs a task in the region owning the given world coordinate.
     *
     * @param world world
     * @param location location
     * @param task task to execute
     */
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
            logger.warning("Bukkit scheduler error: " + e.getMessage());
        }
    }
}
