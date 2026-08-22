package dev.superseller.chestlock.model;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/** A world UUID and block coordinate safe to retain without keeping chunks loaded. */
public record BlockRef(UUID worldId, int x, int y, int z) {
    public static BlockRef of(Block block) {
        return new BlockRef(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public World world() {
        return Bukkit.getWorld(worldId);
    }

    public Location location(World world) {
        return new Location(world, x, y, z);
    }

    public String display() {
        return x + ", " + y + ", " + z;
    }
}
