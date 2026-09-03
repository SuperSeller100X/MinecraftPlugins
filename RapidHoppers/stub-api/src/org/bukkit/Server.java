package org.bukkit;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
public interface Server {
    PluginManager getPluginManager();
    BukkitScheduler getScheduler();
    List<World> getWorlds();
    World getWorld(String name);
    Collection<? extends Player> getOnlinePlayers();
    Inventory createInventory(InventoryHolder holder, int size, Component title);
}
