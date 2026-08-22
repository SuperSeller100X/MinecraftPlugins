package dev.superseller.playerheads.scheduler;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Cross-platform task scheduling with zero compile-time dependencies on
 * Paper/Folia region APIs. Uses Paper's entity/async schedulers when
 * available (via reflection), otherwise falls back to the Bukkit scheduler.
 *
 * <ul>
 *   <li>Async tasks must never touch world or player state.</li>
 *   <li>Entity tasks run on the player's owning region thread (Folia-safe)
 *       and may touch that player's inventory and world.</li>
 * </ul>
 */
public final class PlatformScheduler {

    private static JavaPlugin plugin;
    private static Logger logger;

    private static Method entitySchedulerGet;
    private static Method entityRun;
    private static Method asyncSchedulerGet;
    private static Method asyncRunNow;

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
            // plain spigot or older paper
        }
        try {
            asyncSchedulerGet = Bukkit.class.getMethod("getAsyncScheduler");
            Class<?> asyncScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            asyncRunNow = asyncScheduler.getMethod("runNow", org.bukkit.plugin.Plugin.class, Consumer.class);
        } catch (Throwable ignored) {
            // plain spigot or older paper
        }
    }

    /**
     * Runs a task asynchronously. Safe for Mojang/session-server profile
     * lookups; must not touch world or player state.
     */
    public static void runAsync(Runnable task) {
        if (asyncRunNow != null) {
            try {
                Object scheduler = asyncSchedulerGet.invoke(null);
                asyncRunNow.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run());
                return;
            } catch (Throwable e) {
                logger.warning("Async scheduler failed, falling back to Bukkit scheduler: " + e.getMessage());
            }
        }
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        } catch (Throwable e) {
            logger.warning("Bukkit scheduler unavailable: " + e.getMessage());
        }
    }

    /**
     * Runs a task on the entity's owning thread on the next tick.
     * Safe for inventory changes, messages and world drops.
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
}
