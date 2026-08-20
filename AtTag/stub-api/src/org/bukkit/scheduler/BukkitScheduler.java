package org.bukkit.scheduler;

import org.bukkit.plugin.Plugin;

/**
 * Compile-only stub — never shipped in the plugin jar.
 */
public interface BukkitScheduler {

    BukkitTask runTask(Plugin plugin, Runnable task);
}
