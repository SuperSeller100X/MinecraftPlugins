package org.bukkit;
public class Location implements Cloneable {
    private World world;
    private double x, y, z;
    private float yaw, pitch;
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
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public void setYaw(float yaw) { this.yaw = yaw; }
    public void setPitch(float pitch) { this.pitch = pitch; }
    public Location clone() {
        Location c = new Location(world, x, y, z);
        c.yaw = yaw; c.pitch = pitch; return c;
    }
    public Location add(double dx, double dy, double dz) {
        x += dx; y += dy; z += dz; return this;
    }
}
