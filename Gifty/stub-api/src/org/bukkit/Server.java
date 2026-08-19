package org.bukkit;

import java.util.Collection;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;

public interface Server {
    String getName();
    String getVersion();
    String getBukkitVersion();
    Logger getLogger();
    PluginManager getPluginManager();
    BukkitScheduler getScheduler();
    ServicesManager getServicesManager();
    ConsoleCommandSender getConsoleSender();
    PluginCommand getPluginCommand(String name);
    Collection<? extends Player> getOnlinePlayers();
    Player getPlayer(String name);
    Player getPlayer(UUID id);
    OfflinePlayer getOfflinePlayer(String name);
    OfflinePlayer getOfflinePlayer(UUID id);
    void broadcastMessage(String message);
    Inventory createInventory(InventoryHolder owner, int size, String title);
}
