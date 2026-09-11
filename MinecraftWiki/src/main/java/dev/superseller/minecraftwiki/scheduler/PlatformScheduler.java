package dev.superseller.minecraftwiki.scheduler;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Folia-safe scheduling without a compile-time dependency on Folia classes.
 *
 * <p>On Folia every BukkitScheduler call throws, so this class routes work through the
 * region schedulers reflectively: global-region tasks for plugin wide work and the
 * per-entity scheduler for anything that touches a single player (which is all GUI
 * rendering in this plugin). On Paper, Purpur and Spigot it falls back to the classic
 * {@link org.bukkit.scheduler.BukkitScheduler}.</p>
 */
public final class PlatformScheduler {

    /** A cancellable scheduled task. Cancelling twice is harmless. */
    public interface ScheduledTask {
        void cancel();
    }

    private static final ScheduledTask NOOP = () -> { };

    private static JavaPlugin plugin;
    private static Logger logger;

    private static Method globalSchedulerGet;
    private static Method globalRun;
    private static Method globalRunDelayed;
    private static Method globalRunAtFixedRate;
    private static Method globalCancel;
    private static Method entityScheduler;
    private static Method entityRun;
    private static Method entityRunDelayed;
    private static Method entityCancel;
    private static Method bukkitCancel;

    private static boolean folia;

    private PlatformScheduler() {
    }

    public static void init(JavaPlugin owner) {
        plugin = owner;
        logger = owner.getLogger();
        try {
            globalSchedulerGet = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Class<?> global = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            globalRun = global.getMethod("run", Plugin.class, Consumer.class);
            globalRunDelayed = global.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            globalRunAtFixedRate = global.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            globalCancel = global.getMethod("cancelTasks", Plugin.class);

            entityScheduler = Entity.class.getMethod("getScheduler");
            Class<?> entity = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            entityRun = entity.getMethod("execute", Plugin.class, Runnable.class, Runnable.class, long.class);
            entityRunDelayed = entity.getMethod("run", Plugin.class, Consumer.class, Runnable.class, long.class);
            entityCancel = entity.getMethod("cancelTasks", Plugin.class);
            folia = true;
        } catch (Throwable ignored) {
            folia = false;
        }
        try {
            bukkitCancel = org.bukkit.scheduler.BukkitScheduler.class.getMethod("cancelTasks", Plugin.class);
        } catch (Throwable ignored) {
            bukkitCancel = null;
        }
    }

    /** True when the server exposes Folia's region schedulers. */
    public static boolean isFolia() {
        return folia;
    }

    /** Human readable platform name for {@code /wikiadmin info}. */
    public static String platformName() {
        if (folia) {
            return "Folia";
        }
        try {
            Class.forName("org.purpurmc.purpur.PurpurConfig");
            return "Purpur";
        } catch (Throwable ignored) {
            // not purpur
        }
        try {
            Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
            return "Paper";
        } catch (Throwable ignored) {
            return "Bukkit";
        }
    }

    /** True when the current thread owns the server's main/global region. */
    public static boolean isMainThread() {
        if (plugin == null) {
            return false;
        }
        if (folia) {
            try {
                return Bukkit.class.getMethod("isGlobalRegionThread").invoke(null) instanceof Boolean b && b;
            } catch (Throwable ignored) {
                return false;
            }
        }
        return Bukkit.isPrimaryThread();
    }

    /** Runs work on the main thread (or the caller's thread when already there). */
    public static void runGlobal(Runnable task) {
        if (task == null) {
            return;
        }
        if (isMainThread()) {
            task.run();
            return;
        }
        if (globalRun != null) {
            try {
                Object scheduler = globalSchedulerGet.invoke(null);
                globalRun.invoke(scheduler, plugin, (Consumer<Object>) ignored -> task.run());
                return;
            } catch (Throwable error) {
                warn("global run", error);
            }
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    /** Runs work on the main thread after a delay in ticks. */
    public static ScheduledTask runGlobalLater(Runnable task, long delayTicks) {
        if (task == null) {
            return NOOP;
        }
        long delay = Math.max(1L, delayTicks);
        if (globalRunDelayed != null) {
            try {
                Object scheduler = globalSchedulerGet.invoke(null);
                Object handle = globalRunDelayed.invoke(scheduler, plugin,
                        (Consumer<Object>) ignored -> task.run(), delay);
                return bukkitHandle(handle);
            } catch (Throwable error) {
                warn("global delayed run", error);
            }
        }
        return bukkitHandle(Bukkit.getScheduler().runTaskLater(plugin, task, delay));
    }

    /** Starts a repeating main-thread task. */
    public static ScheduledTask runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        if (task == null) {
            return NOOP;
        }
        long delay = Math.max(1L, delayTicks);
        long period = Math.max(1L, periodTicks);
        if (globalRunAtFixedRate != null) {
            try {
                Object scheduler = globalSchedulerGet.invoke(null);
                Object handle = globalRunAtFixedRate.invoke(scheduler, plugin,
                        (Consumer<Object>) ignored -> task.run(), delay, period);
                return bukkitHandle(handle);
            } catch (Throwable error) {
                warn("global timer", error);
            }
        }
        return bukkitHandle(Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period));
    }

    /**
     * Runs work on the thread that owns the given entity. GUI rendering always goes
     * through here so it is correct on Folia as well as on Paper.
     */
    public static void runEntity(Entity entity, Runnable task) {
        if (entity == null || task == null) {
            return;
        }
        if (entityRun != null) {
            try {
                Object scheduler = entityScheduler.invoke(entity);
                if (scheduler != null) {
                    // The retired callback is intentionally empty: the player is gone.
                    entityRun.invoke(scheduler, plugin, task, (Runnable) () -> { }, 1L);
                    return;
                }
            } catch (Throwable error) {
                warn("entity run", error);
            }
        }
        if (isMainThread()) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    /** Runs work on the entity's thread after a delay in ticks. */
    public static ScheduledTask runEntityLater(Entity entity, Runnable task, long delayTicks) {
        if (entity == null || task == null) {
            return NOOP;
        }
        long delay = Math.max(1L, delayTicks);
        if (entityRunDelayed != null) {
            try {
                Object scheduler = entityScheduler.invoke(entity);
                if (scheduler != null) {
                    Object handle = entityRunDelayed.invoke(scheduler, plugin,
                            (Consumer<Object>) ignored -> task.run(), (Runnable) () -> { }, delay);
                    return bukkitHandle(handle);
                }
            } catch (Throwable error) {
                warn("entity delayed run", error);
            }
        }
        return runGlobalLater(task, delay);
    }

    /** Cancels every task this plugin owns. */
    public static void cancelTasks() {
        if (plugin == null) {
            return;
        }
        try {
            if (globalCancel != null) {
                globalCancel.invoke(globalSchedulerGet.invoke(null), plugin);
            }
            if (entityCancel != null) {
                for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                    Object scheduler = entityScheduler.invoke(player);
                    if (scheduler != null) {
                        entityCancel.invoke(scheduler, plugin);
                    }
                }
            }
            if (bukkitCancel != null) {
                bukkitCancel.invoke(Bukkit.getScheduler(), plugin);
            }
        } catch (Throwable error) {
            warn("cancel tasks", error);
        }
    }

    private static ScheduledTask bukkitHandle(Object handle) {
        if (handle == null) {
            return NOOP;
        }
        AtomicBoolean cancelled = new AtomicBoolean(false);
        return () -> {
            if (cancelled.compareAndSet(false, true)) {
                try {
                    if (handle instanceof org.bukkit.scheduler.BukkitTask bukkitTask) {
                        bukkitTask.cancel();
                        return;
                    }
                    handle.getClass().getMethod("cancel").invoke(handle);
                } catch (Throwable error) {
                    warn("cancel", error);
                }
            }
        };
    }

    private static void warn(String what, Throwable error) {
        if (logger != null) {
            logger.warning("[Wiki] Scheduling failure during " + what + ": " + error);
        }
    }
}
