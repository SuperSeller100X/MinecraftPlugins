package org.bukkit;
import java.util.Collection;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
public final class Bukkit {
    public static Server getServer() { return null; }
    public static BukkitScheduler getScheduler() { return null; }
    public static Collection<? extends Player> getOnlinePlayers() { return java.util.List.of(); }
    public static Player getPlayerExact(String name) { return null; }
    public static OfflinePlayer getOfflinePlayerIfCached(String name) { return null; }
    public static PluginManager getPluginManager() { return null; }
    public static Inventory createInventory(InventoryHolder owner, int size, Component title) { return null; }
    public static org.bukkit.command.CommandSender getConsoleSender() { return null; }
    public static boolean dispatchCommand(org.bukkit.command.CommandSender sender, String command) { return true; }
}
