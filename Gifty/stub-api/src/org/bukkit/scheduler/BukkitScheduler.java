package org.bukkit.scheduler;

import org.bukkit.plugin.Plugin;

public interface BukkitScheduler {
    BukkitTask runTask(Plugin plugin, Runnable task);
    BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay);
    BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period);
    BukkitTask runTaskAsynchronously(Plugin plugin, Runnable task);
    void cancelTasks(Plugin plugin);
}
