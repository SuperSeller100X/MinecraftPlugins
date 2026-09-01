package org.bukkit;
import java.util.Collection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;
public interface Server {
    PluginManager getPluginManager();
    ServicesManager getServicesManager();
    BukkitScheduler getScheduler();
    Collection<? extends Player> getOnlinePlayers();
    Player getPlayerExact(String name);
    OfflinePlayer getOfflinePlayerIfCached(String name);
}
