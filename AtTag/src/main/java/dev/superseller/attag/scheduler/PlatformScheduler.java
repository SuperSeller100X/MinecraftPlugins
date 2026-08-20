package dev.superseller.attag.scheduler;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Cross-platform task scheduling with zero compile-time dependencies on
 * Paper/Folia APIs. Uses Paper's entity/global region schedulers when
 * available (via reflection), otherwise falls back to the Bukkit scheduler.
 *
 * - Entity tasks run on the player's owning region thread (Folia-safe).
 * - Global tasks run on the main/global thread.
 */
public final class PlatformScheduler {

    private static JavaPlugin plugin;
    private static Logger logger;

    private static Method entitySchedulerGet;
    private static Method entityRun;
    private static Method globalSchedulerGet;
    private static Method globalRun;

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
            paperSchedulers = true;
        } catch (Throwable ignored) {
            // plain spigot
        }
    }

    /** True when Paper's region schedulers are available (Paper/Purpur/Folia). */
    public static boolean isPaperSchedulers() {
        return paperSchedulers;
    }

    /**
     * Runs a task on the entity's owning thread on the next tick.
     * Safe for touching players, playing sounds and reading world state.
     */
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
}
