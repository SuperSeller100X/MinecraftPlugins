package org.bukkit.scheduler;
import org.bukkit.plugin.Plugin;
public interface BukkitScheduler {
    BukkitTask runTask(Plugin plugin, Runnable task);
    int scheduleSyncRepeatingTask(Plugin plugin, Runnable task, long delay, long period);
    void cancelTask(int taskId);
    void cancelTasks(Plugin plugin);
}
