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

    /** Stable one-line form used to persist access blocks in a container's PDC. */
    public String serialize() {
        return worldId + ";" + x + ";" + y + ";" + z;
    }

    /** Inverse of {@link #serialize()}. Returns {@code null} for malformed input. */
    public static BlockRef parse(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split(";");
        if (parts.length != 4) {
            return null;
        }
        try {
            return new BlockRef(UUID.fromString(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
