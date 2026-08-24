package org.bukkit;
public class Location {
    public Location(World world, double x, double y, double z) {}
    public World getWorld() { return null; }
    public double getX() { return 0; }
    public double getY() { return 0; }
    public double getZ() { return 0; }
    public Vector getDirection() { return new Vector(); }
    public Location add(double x, double y, double z) { return this; }
}
