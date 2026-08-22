package dev.superseller.chunkvoter.scheduler;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Cross-platform task scheduling with zero compile-time dependencies on
 * Paper/Folia APIs. Uses Paper's entity/global/region schedulers when
 * available (via reflection), otherwise falls back to the Bukkit scheduler.
 *
 * <p>Conventions (important on Folia):
 * <ul>
 *   <li>Entity tasks run on the player's owning region thread.</li>
 *   <li>Global repeating tasks must only touch shared data / files, never
 *       world or player state.</li>
 *   <li>Region tasks run on the region that owns a location, so world/chunk
 *       work is thread-safe.</li>
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
    private static Method regionSchedulerGet;
    private static Method regionExecute;
    private static Method regionRun;

    private static boolean paperSchedulers = false;

    private PlatformScheduler() {
    }

    public static void init(JavaPlugin pl) {
        plugin = pl;
        logger = pl.getLogger();
        try {
            entitySchedulerGet = Player.class.getMethod("getScheduler");
            Class<?> entityScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            entityRun = entityScheduler.getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class, Runnable.class);
            paperSchedulers = true;
        } catch (Throwable ignored) {
            // plain spigot or older paper
        }
        try {
            globalSchedulerGet = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Class<?> globalScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            globalRun = globalScheduler.getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class);
            globalRunAtFixedRate = globalScheduler.getMethod("runAtFixedRate", org.bukkit.plugin.Plugin.class,
                    Consumer.class, long.class, long.class);
            paperSchedulers = true;
        } catch (Throwable ignored) {
            // plain spigot
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
            paperSchedulers = true;
        } catch (Throwable ignored) {
            // plain spigot
        }
    }

    /** True when Paper's region schedulers are available (Paper/Purpur/Folia). */
    public static boolean isPaperSchedulers() {
        return paperSchedulers;
    }

    /** Runs a task on the entity's owning region thread on the next tick. */
    public static void runEntitySync(Player player, Runnable task) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (entityRun != null) {
            try {
                Object scheduler = entitySchedulerGet.invoke(player);
                entityRun.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run(), (Runnable) null);
                return;
            } catch (Throwable e) {
                logger.warning("Entity scheduler failed, falling back to Bukkit scheduler: " + e.getMessage());
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
                logger.warning("Global scheduler failed, falling back to Bukkit scheduler: " + e.getMessage());
            }
        }
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (Throwable e) {
            logger.warning("Bukkit scheduler unavailable: " + e.getMessage());
        }
    }

    /** Starts a repeating task that is safe to run from the global thread. */
    public static void runTimer(Runnable task, long periodTicks) {
        if (globalRunAtFixedRate != null) {
            try {
                Object scheduler = globalSchedulerGet.invoke(null);
                globalRunAtFixedRate.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run(), 1L, periodTicks);
                return;
            } catch (Throwable e) {
                logger.warning("Global timer failed, falling back to Bukkit scheduler: " + e.getMessage());
            }
        }
        try {
            Bukkit.getScheduler().runTaskTimer(plugin, task, 1L, periodTicks);
        } catch (Throwable e) {
            logger.warning("Bukkit scheduler unavailable: " + e.getMessage());
        }
    }

    /**
     * Runs a task on the region thread that owns the given chunk coordinates.
     * Falls back to the global scheduler when the region scheduler is not
     * available. Safe for world/chunk mutation on Paper, Purpur and Folia.
     */
    public static void runRegionSync(World world, int chunkX, int chunkZ, Runnable task) {
        if (world == null) {
            runGlobal(task);
            return;
        }
        if (regionSchedulerGet != null) {
            try {
                Object scheduler = regionSchedulerGet.invoke(null);
                Location loc = new Location(world, (chunkX << 4) + 8, 64.0, (chunkZ << 4) + 8);
                if (regionExecute != null) {
                    regionExecute.invoke(scheduler, plugin, loc, task);
                    return;
                }
                if (regionRun != null) {
                    regionRun.invoke(scheduler, plugin, loc, (Consumer<Object>) t -> task.run());
                    return;
                }
            } catch (Throwable e) {
                logger.warning("Region scheduler failed, falling back to global scheduler: " + e.getMessage());
            }
        }
        runGlobal(task);
    }
}
