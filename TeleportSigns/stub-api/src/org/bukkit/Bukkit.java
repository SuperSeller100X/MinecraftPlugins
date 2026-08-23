package org.bukkit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;
public final class Bukkit {
    private static Server server;
    private Bukkit() {}
    public static Server getServer() { return server; }
    public static void setServer(Server s) { server = s; }
    public static org.bukkit.plugin.PluginManager getPluginManager() { return server.getPluginManager(); }
    public static ServicesManager getServicesManager() { return server.getServicesManager(); }
    public static BukkitScheduler getScheduler() { return server.getScheduler(); }
    public static World getWorld(String name) { return null; }
    public static List<World> getWorlds() { return new ArrayList<World>(); }
    public static OfflinePlayer getOfflinePlayer(UUID uuid) { return new OfflinePlayer() {
        public UUID getUniqueId() { return uuid; }
        public String getName() { return "offline"; }
    }; }
}
