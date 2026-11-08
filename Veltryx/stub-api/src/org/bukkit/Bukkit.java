package org.bukkit;

import java.util.Collection;
import java.util.UUID;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitScheduler;
import net.kyori.adventure.text.Component;

public final class Bukkit {
    private Bukkit() {}
    public static Server getServer() { return null; }
    public static Collection<? extends Player> getOnlinePlayers() { return null; }
    public static java.util.List<World> getWorlds() { return null; }
    public static World getWorld(String name) { return null; }
    public static Player getPlayerExact(String name) { return null; }
    public static Player getPlayer(UUID id) { return null; }
    @Deprecated public static OfflinePlayer getOfflinePlayer(String name) { return null; }
    public static OfflinePlayer getOfflinePlayer(java.util.UUID id) { return null; }
    public static ConsoleCommandSender getConsoleSender() { return null; }
    public static BukkitScheduler getScheduler() { return null; }
    public static double[] getTPS() { return null; }
    public static Inventory createInventory(InventoryHolder owner, int size, Component title) { return null; }
}
