package org.bukkit;

import org.bukkit.block.Block;

public class Location implements Cloneable {
    private final World world; private final double x, y, z; private final float yaw, pitch;
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
    public Block getBlock() { return null; }
    public org.bukkit.util.Vector getDirection() { return null; }
    public org.bukkit.util.Vector toVector() { return null; }
    public Location add(double dx, double dy, double dz) { return this; }
    public Location subtract(double dx, double dy, double dz) { return this; }
    public double distance(Location other) { return 0; }
    @Override public Location clone() { return this; }
}
