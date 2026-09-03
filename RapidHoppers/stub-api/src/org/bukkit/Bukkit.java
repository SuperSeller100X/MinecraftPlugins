package org.bukkit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitScheduler;
public final class Bukkit {
    private static Server server;
    private Bukkit() {}
    public static Server getServer() { return server; }
    public static void setServer(Server s) { server = s; }
    public static org.bukkit.plugin.PluginManager getPluginManager() { return server.getPluginManager(); }
    public static BukkitScheduler getScheduler() { return server.getScheduler(); }
    public static List<World> getWorlds() { return server == null ? new ArrayList<World>() : server.getWorlds(); }
    public static World getWorld(String name) { return server == null ? null : server.getWorld(name); }
    public static Collection<? extends Player> getOnlinePlayers() {
        return server == null ? new ArrayList<Player>() : server.getOnlinePlayers();
    }
    public static Inventory createInventory(InventoryHolder holder, int size, Component title) {
        return server.createInventory(holder, size, title);
    }
}
