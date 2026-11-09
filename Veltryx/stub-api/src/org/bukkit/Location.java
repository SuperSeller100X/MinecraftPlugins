package org.bukkit;

import java.util.Objects;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

/** Minimal mutable location matching the Bukkit operations used in tests. */
public class Location implements Cloneable {
    private final World world;
    private double x, y, z;
    private final float yaw, pitch;
    public Location(World world, double x, double y, double z) { this(world, x, y, z, 0f, 0f); }
    public Location(World world, double x, double y, double z, float yaw, float pitch) {
        this.world = world; this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
    }
    public World getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public int getBlockX() { return (int) Math.floor(x); }
    public int getBlockY() { return (int) Math.floor(y); }
    public int getBlockZ() { return (int) Math.floor(z); }
    public Block getBlock() { return world == null ? null : world.getBlockAt(getBlockX(), getBlockY(), getBlockZ()); }
    public Vector getDirection() {
        double yawRadians = Math.toRadians(yaw), pitchRadians = Math.toRadians(pitch);
        double horizontal = Math.cos(pitchRadians);
        return new Vector(-horizontal * Math.sin(yawRadians), -Math.sin(pitchRadians), horizontal * Math.cos(yawRadians));
    }
    public Vector toVector() { return new Vector(x, y, z); }
    public Location add(double dx, double dy, double dz) { x += dx; y += dy; z += dz; return this; }
    public Location subtract(double dx, double dy, double dz) { return add(-dx, -dy, -dz); }
    public double distance(Location other) {
        if (other == null || !Objects.equals(world, other.world)) throw new IllegalArgumentException("Different worlds");
        double dx = x - other.x, dy = y - other.y, dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    @Override public Location clone() { return new Location(world, x, y, z, yaw, pitch); }
}
