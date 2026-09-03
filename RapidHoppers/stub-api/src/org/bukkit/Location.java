package org.bukkit;
import org.bukkit.block.Block;
public class Location implements Cloneable {
    private World world;
    private double x, y, z;
    public Location(World world, double x, double y, double z) {
        this.world = world; this.x = x; this.y = y; this.z = z;
    }
    public World getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public int getBlockX() { return (int) Math.floor(x); }
    public int getBlockY() { return (int) Math.floor(y); }
    public int getBlockZ() { return (int) Math.floor(z); }
    public Block getBlock() { return world == null ? null : world.getChunkAt(getBlockX() >> 4, getBlockZ() >> 4) == null ? null : null; }
    public Location add(double dx, double dy, double dz) { x += dx; y += dy; z += dz; return this; }
    public Location clone() { return new Location(world, x, y, z); }
}
