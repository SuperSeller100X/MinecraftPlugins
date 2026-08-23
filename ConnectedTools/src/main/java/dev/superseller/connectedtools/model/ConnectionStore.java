package dev.superseller.connectedtools.model;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ConnectionStore {

    private final File file;
    private final Map<UUID, List<Connection>> data = new HashMap<>();
    private final Set<UUID> bindingPlayers = new HashSet<>();

    public ConnectionStore(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "connections.yml");
        load();
    }

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
        List<Connection> list = data.getOrDefault(player, Collections.emptyList());
        String itemName = item.getType().name();
        for (Connection c : list) {
            if (c.getItemType() == item.getType() && c.getItemName().equalsIgnoreCase(itemName)) {
                return c;
            }
        }
        return null;
    }

    public List<Connection> getConnections(UUID player) {
        return new ArrayList<>(data.getOrDefault(player, Collections.emptyList()));
    }

    public void addConnection(UUID player, org.bukkit.inventory.ItemStack item, Connection connection) {
        data.computeIfAbsent(player, k -> new ArrayList<>()).add(connection);
        save();
    }

    public void removeConnection(UUID player, org.bukkit.inventory.ItemStack item) {
        List<Connection> list = data.get(player);
        if (list == null) return;
        String itemName = item.getType().name();
        list.removeIf(c -> c.getItemType() == item.getType() && c.getItemName().equalsIgnoreCase(itemName));
        save();
    }

    public void removeConnection(UUID player, Connection connection) {
        List<Connection> list = data.get(player);
        if (list == null) return;
        list.removeIf(c -> c.getSerializedLocation().equals(connection.getSerializedLocation())
                && c.getItemType() == connection.getItemType());
        save();
    }

    public void load() {
        if (!file.exists()) return;
        try {
            org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            for (String uuidStr : yaml.getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                List<Connection> connections = new ArrayList<>();
                for (String key : yaml.getConfigurationSection(uuidStr).getKeys(false)) {
                    String itemName = yaml.getString(uuidStr + "." + key + ".item-name", "");
                    String materialName = yaml.getString(uuidStr + "." + key + ".material", "");
                    String serializedLoc = yaml.getString(uuidStr + "." + key + ".location", "");
                    Material mat = Material.getMaterial(materialName);
                    if (mat == null || serializedLoc == null || serializedLoc.isEmpty()) continue;
                    Connection conn = Connection.fromSerialized(uuid, itemName, mat, serializedLoc);
                    if (conn != null) connections.add(conn);
                }
                if (!connections.isEmpty()) {
                    data.put(uuid, connections);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void save() {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        int index = 0;
        for (Map.Entry<UUID, List<Connection>> entry : data.entrySet()) {
            String uuidStr = entry.getKey().toString();
            for (Connection conn : entry.getValue()) {
                String key = "c" + (index++);
                yaml.set(uuidStr + "." + key + ".item-name", conn.getItemName());
                yaml.set(uuidStr + "." + key + ".material", conn.getItemType().name());
                yaml.set(uuidStr + "." + key + ".location", conn.getSerializedLocation());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
