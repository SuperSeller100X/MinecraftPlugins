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
    // Hooks used exclusively by the offline GUI tests; these stubs are never packaged.
    public static org.bukkit.scheduler.BukkitScheduler testScheduler;
    public static java.util.function.BiFunction<InventoryHolder, Integer, Inventory> testInventoryFactory;
    public static java.util.Collection<? extends Player> testOnlinePlayers = java.util.List.of();
    public static java.util.function.Function<World, org.bukkit.map.MapView> testMapFactory;
    public static java.util.function.Function<NamespacedKey, org.bukkit.loot.LootTable> testLootFactory;
    public static org.bukkit.loot.LootTable getLootTable(NamespacedKey key) { return testLootFactory == null ? null : testLootFactory.apply(key); }
    public static org.bukkit.map.MapView createMap(World world) { return testMapFactory == null ? null : testMapFactory.apply(world); }
    public static Server getServer() { return null; }
    public static Collection<? extends Player> getOnlinePlayers() { return testOnlinePlayers; }
    public static java.util.List<World> getWorlds() { return null; }
    public static org.bukkit.scoreboard.ScoreboardManager testScoreboardManager;
    public static org.bukkit.scoreboard.ScoreboardManager getScoreboardManager() { return testScoreboardManager; }
    public static World getWorld(String name) { return null; }
    public static Player getPlayerExact(String name) { return null; }
    public static Player getPlayer(UUID id) { for (Player p : testOnlinePlayers) if (p.getUniqueId().equals(id)) return p; return null; }
    @Deprecated public static OfflinePlayer getOfflinePlayer(String name) { return null; }
    public static OfflinePlayer getOfflinePlayer(java.util.UUID id) { return null; }
    public static ConsoleCommandSender getConsoleSender() { return null; }
    public static BukkitScheduler getScheduler() { return testScheduler; }
    public static double[] getTPS() { return null; }
    public static Inventory createInventory(InventoryHolder owner, int size, Component title) { return testInventoryFactory == null ? null : testInventoryFactory.apply(owner, size); }
}
