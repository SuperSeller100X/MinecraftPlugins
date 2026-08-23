package dev.superseller.connectedtools.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.UUID;

public class Connection {

    private final UUID owner;
    private final String itemName;
    private final Material itemType;
    private final String targetType;
    private final String worldName;
    private final int x, y, z;

    public Connection(UUID owner, String itemName, Material itemType, Location target) {
        this.owner = owner;
        this.itemName = itemName;
        this.itemType = itemType;
        this.targetType = target.getBlock().getType().name();
        this.worldName = target.getWorld() != null ? target.getWorld().getName() : "world";
        this.x = target.getBlockX();
        this.y = target.getBlockY();
        this.z = target.getBlockZ();
    }

    public UUID getOwner() {
        return owner;
    }

    public String getItemName() {
        return itemName;
    }

    public Material getItemType() {
        return itemType;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getLocationString() {
        return worldName + " [" + x + ", " + y + ", " + z + "]";
    }

    public String getSerializedLocation() {
        return worldName + ";" + x + ";" + y + ";" + z;
    }

    public Location getLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }

    public static Connection fromSerialized(UUID owner, String itemName, Material itemType, String serializedLoc) {
        String[] parts = serializedLoc.split(";");
        if (parts.length != 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        int x, y, z;
        try {
            x = Integer.parseInt(parts[1]);
            y = Integer.parseInt(parts[2]);
            z = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            return null;
        }
        return new Connection(owner, itemName, itemType, new Location(world, x, y, z));
    }
}
