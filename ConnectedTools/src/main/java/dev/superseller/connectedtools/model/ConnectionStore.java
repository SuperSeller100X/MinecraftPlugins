package dev.superseller.connectedtools.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

public class ConnectionStore {

    private final Map<UUID, Map<String, Connection>> data = new HashMap<>();
    private final Set<UUID> bindingPlayers = new HashSet<>();

    public void setBindingPlayer(UUID uuid) {
        bindingPlayers.add(uuid);
    }

    public boolean isBinding(UUID uuid) {
        return bindingPlayers.contains(uuid);
    }

    public void clearBinding(UUID uuid) {
        bindingPlayers.remove(uuid);
    }

    public Connection getConnection(UUID player, org.bukkit.inventory.ItemStack item) {
        String key = itemKey(item);
        Map<String, Connection> playerData = data.getOrDefault(player, Collections.emptyMap());
        return playerData.get(key);
    }

    public java.util.List<Connection> getConnections(UUID player) {
        Map<String, Connection> playerData = data.getOrDefault(player, Collections.emptyMap());
        return new java.util.ArrayList<>(playerData.values());
    }

    public void addConnection(UUID player, org.bukkit.inventory.ItemStack item, Connection connection) {
        data.computeIfAbsent(player, k -> new HashMap<>()).put(itemKey(item), connection);
    }

    public void removeConnection(UUID player, org.bukkit.inventory.ItemStack item) {
        Map<String, Connection> playerData = data.get(player);
        if (playerData != null) {
            playerData.remove(itemKey(item));
        }
    }

    public void removeConnection(UUID player, String itemKey) {
        Map<String, Connection> playerData = data.get(player);
        if (playerData != null) {
            playerData.remove(itemKey);
        }
    }

    private String itemKey(org.bukkit.inventory.ItemStack item) {
        String meta = item.hasItemMeta() ? item.getItemMeta().getAsString() : "";
        return item.getType().name() + ":" + meta.hashCode();
    }

    public java.util.Map<java.util.UUID, java.util.Map<String, Connection>> getData() {
        return data;
    }

    public static String serializeLocation(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    public static Location deserializeLocation(String s) {
        String[] parts = s.split(";");
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        return new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }
}
