package org.bukkit;

import java.util.Collection;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;

public interface Server {
    PluginManager getPluginManager();
    Collection<? extends Player> getOnlinePlayers();
    ConsoleCommandSender getConsoleSender();
    BukkitScheduler getScheduler();
}
