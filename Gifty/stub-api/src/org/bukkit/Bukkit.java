package org.bukkit;

import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;

public final class Bukkit {
    private static Server server;

    private Bukkit() {
    }

    /** Test hook — stubs are compile-only and never shipped. */
    public static void setServer(Server s) {
        server = s;
    }

    public static Server getServer() {
        return server;
    }

    public static PluginManager getPluginManager() {
        return server == null ? null : server.getPluginManager();
    }

    public static BukkitScheduler getScheduler() {
        return server == null ? null : server.getScheduler();
    }

    public static ServicesManager getServicesManager() {
        return server == null ? null : server.getServicesManager();
    }

    public static Logger getLogger() {
        return server == null ? Logger.getGlobal() : server.getLogger();
    }

    public static ConsoleCommandSender getConsoleSender() {
        return server == null ? null : server.getConsoleSender();
    }

    public static Player getPlayer(String name) {
        return server == null ? null : server.getPlayer(name);
    }

    public static Player getPlayer(UUID id) {
        return server == null ? null : server.getPlayer(id);
    }

    public static OfflinePlayer getOfflinePlayer(String name) {
        return server == null ? null : server.getOfflinePlayer(name);
    }

    public static OfflinePlayer getOfflinePlayer(UUID id) {
        return server == null ? null : server.getOfflinePlayer(id);
    }

    public static java.util.Collection<? extends Player> getOnlinePlayers() {
        return server == null ? null : server.getOnlinePlayers();
    }

    public static String getVersion() {
        return server == null ? null : server.getVersion();
    }

    public static String getBukkitVersion() {
        return server == null ? null : server.getBukkitVersion();
    }

    public static Inventory createInventory(InventoryHolder owner, int size, String title) {
        return server == null ? null : server.createInventory(owner, size, title);
    }
}
