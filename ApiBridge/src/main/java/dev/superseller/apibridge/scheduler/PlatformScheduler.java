package dev.superseller.apibridge.scheduler;

import dev.superseller.apibridge.core.MinecraftExecutor;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlatformScheduler implements MinecraftExecutor {
    private final JavaPlugin plugin;
    private final Logger logger;
    private Method globalSchedulerGet;
    private Method globalRun;

    public PlatformScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        try {
            globalSchedulerGet = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Class<?> globalScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            globalRun = globalScheduler.getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class);
        } catch (Throwable ignored) {
            globalSchedulerGet = null;
            globalRun = null;
        }
    }

    @Override
    public void runGlobal(Runnable task) {
        if (globalSchedulerGet != null && globalRun != null) {
            try {
                Object scheduler = globalSchedulerGet.invoke(null);
                globalRun.invoke(scheduler, plugin, (Consumer<Object>) unused -> task.run());
                return;
            } catch (Throwable e) {
                logger.warning("api_event=scheduler_fallback reason=\"" + safe(e.getMessage()) + "\"");
            }
        }
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (Throwable e) {
            logger.warning("api_event=scheduler_failure reason=\"" + safe(e.getMessage()) + "\"");
        }
    }

    private String safe(String message) {
        return message == null ? "" : message.replace('\n', ' ').replace('\r', ' ');
    }
}
