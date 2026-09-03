package dev.superseller.rapidhoppers.scheduler;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Cross-platform scheduling with zero compile-time dependency on Folia classes.
 *
 * <p>On Folia the plugin uses the {@code GlobalRegionScheduler} (and, where a
 * location is known, the {@code RegionScheduler}); on Paper/Purpur/Spigot it
 * falls back to the classic {@link org.bukkit.scheduler.BukkitScheduler}. All
 * lookups are reflective so a single jar runs on every platform, on Linux,
 * Windows and macOS alike.</p>
 */
public final class PlatformScheduler {

    private static JavaPlugin plugin;
    private static Logger logger;

    private static Method globalSchedulerGet;
    private static Method globalRun;
    private static Method globalRunAtFixedRate;
    private static Method globalCancelTasks;

    private static boolean folia;

    private PlatformScheduler() {
    }

    public static void init(JavaPlugin pl) {
        plugin = pl;
        logger = pl.getLogger();
        try {
            globalSchedulerGet = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Class<?> global = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            globalRun = global.getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class);
            globalRunAtFixedRate = global.getMethod("runAtFixedRate",
                    org.bukkit.plugin.Plugin.class, Consumer.class, long.class, long.class);
            globalCancelTasks = global.getMethod("cancelTasks", org.bukkit.plugin.Plugin.class);
            folia = true;
        } catch (Throwable ignored) {
            folia = false;
        }
    }

    /** True when the server exposes Folia's region schedulers. */
    public static boolean isFolia() {
        return folia;
    }

    /** Human readable platform name for {@code /rh info}. */
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

    /** Runs a task once on the global/main thread as soon as possible. */
    public static void runGlobal(Runnable task) {
        if (globalRun != null) {
            try {
                Object scheduler = globalSchedulerGet.invoke(null);
                globalRun.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run());
                return;
            } catch (Throwable e) {
                warn("global scheduler", e);
            }
        }
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (Throwable e) {
            warn("bukkit scheduler", e);
        }
    }

    /**
     * Repeating task on the global/main thread.
     *
     * @return a handle that cancels the task on both platforms
     */
    public static Handle runTimer(Runnable task, long delayTicks, long periodTicks) {
        long delay = Math.max(1L, delayTicks);
        long period = Math.max(1L, periodTicks);
        if (globalRunAtFixedRate != null) {
            try {
                Object scheduler = globalSchedulerGet.invoke(null);
                Object handle = globalRunAtFixedRate.invoke(scheduler, plugin,
                        (Consumer<Object>) t -> task.run(), delay, period);
                return new Handle(handle, -1);
            } catch (Throwable e) {
                warn("global repeating scheduler", e);
            }
        }
        try {
            int id = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, task, delay, period);
            return new Handle(null, id);
        } catch (Throwable e) {
            warn("bukkit repeating scheduler", e);
            return new Handle(null, -1);
        }
    }

    /**
     * Runs a task that touches blocks/entities at {@code location} on the
     * thread that owns that region. On Paper/Purpur/Spigot the work runs
     * immediately (we are already on the main thread).
     */
    public static void runAtLocation(org.bukkit.Location location, Runnable task) {
        if (!folia) {
            task.run();
            return;
        }
        try {
            Method get = Bukkit.class.getMethod("getRegionScheduler");
            Object scheduler = get.invoke(null);
            Method run = scheduler.getClass().getMethod("execute",
                    org.bukkit.plugin.Plugin.class, org.bukkit.Location.class, Runnable.class);
            run.invoke(scheduler, plugin, location, task);
        } catch (Throwable e) {
            warn("region scheduler", e);
        }
    }

    /** Cancels every task owned by this plugin. */
    public static void cancelAll() {
        if (globalCancelTasks != null) {
            try {
                Object scheduler = globalSchedulerGet.invoke(null);
                globalCancelTasks.invoke(scheduler, plugin);
            } catch (Throwable ignored) {
                // fall through to Bukkit
            }
        }
        try {
            Bukkit.getScheduler().cancelTasks(plugin);
        } catch (Throwable ignored) {
            // nothing else we can do during shutdown
        }
    }

    private static void warn(String what, Throwable e) {
        if (logger != null) {
            logger.warning("RapidHoppers " + what + " unavailable: " + e.getMessage());
        }
    }

    /** Opaque cancellable task handle covering both scheduler families. */
    public static final class Handle {
        private final Object foliaTask;
        private final int bukkitId;

        Handle(Object foliaTask, int bukkitId) {
            this.foliaTask = foliaTask;
            this.bukkitId = bukkitId;
        }

        public void cancel() {
            if (foliaTask != null) {
                try {
                    foliaTask.getClass().getMethod("cancel").invoke(foliaTask);
                } catch (Throwable ignored) {
                    // already gone
                }
                return;
            }
            if (bukkitId >= 0) {
                try {
                    Bukkit.getScheduler().cancelTask(bukkitId);
                } catch (Throwable ignored) {
                    // already gone
                }
            }
        }
    }
}
