package org.bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;
public interface Server {
    PluginManager getPluginManager();
    ServicesManager getServicesManager();
    BukkitScheduler getScheduler();
}
