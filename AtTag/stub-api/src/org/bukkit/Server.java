package org.bukkit;

import java.util.Collection;

import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;

/**
 * Compile-only stub — never shipped in the plugin jar.
 */
public interface Server {

    Collection<? extends Player> getOnlinePlayers();

    PluginManager getPluginManager();

    BukkitScheduler getScheduler();
}
