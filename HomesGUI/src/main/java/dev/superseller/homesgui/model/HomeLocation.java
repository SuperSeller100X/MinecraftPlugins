package dev.superseller.homesgui.model;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.World;

/** A world-independent, immutable copy of a player's saved position. */
public record HomeLocation(String world, double x, double y, double z, float yaw, float pitch) {

    public HomeLocation {
        world = Objects.requireNonNull(world, "world").trim();
        if (world.isEmpty()) {
            throw new IllegalArgumentException("world must not be blank");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("home coordinates and rotation must be finite");
        }
    }

    /** Copies a live Bukkit location while it is being accessed on its owning region. */
    public static HomeLocation from(Location location) {
        Objects.requireNonNull(location, "location");
        World world = Objects.requireNonNull(location.getWorld(), "location world");
        return new HomeLocation(world.getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    public Location toLocation(World targetWorld) {
        return new Location(Objects.requireNonNull(targetWorld, "targetWorld"), x, y, z, yaw, pitch);
    }
}
