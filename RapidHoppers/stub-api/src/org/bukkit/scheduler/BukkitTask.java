package org.bukkit.scheduler;
public interface BukkitTask {
    int getTaskId();
    void cancel();
}
