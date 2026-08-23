package dev.superseller.connectedtools.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class PlatformScheduler {

    private final JavaPlugin plugin;

    public PlatformScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public BukkitTask runTaskLater(Runnable task, long ticks) {
        return Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
    }

    public boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
