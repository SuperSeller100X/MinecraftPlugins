package dev.superseller.connectedtools.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public class Connection {

    private final String itemName;
    private final Material itemType;
    private final String targetBlockType;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;

    public Connection(String itemName, Material itemType, Location target) {
        this.itemName = itemName;
        this.itemType = itemType;
        this.targetBlockType = target.getBlock().getType().name();
        this.worldName = target.getWorld().getName();
        this.x = target.getBlockX();
        this.y = target.getBlockY();
        this.z = target.getBlockZ();
    }

    public String getItemName() {
        return itemName;
    }

    public Material getItemType() {
        return itemType;
    }

    public String getTargetType() {
        return targetBlockType;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getLocationString() {
        return worldName + " [" + x + ", " + y + ", " + z + "]";
    }

    public Location getLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }

    public String getSerializedLocation() {
        return ConnectionStore.serializeLocation(getLocation());
    }
}
