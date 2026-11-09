package org.bukkit.util;

/** Minimal, block-local-compatible bounding box used by the offline tests. */
public class BoundingBox {
    private final double minX, minY, minZ, maxX, maxY, maxZ;

    public BoundingBox() { this(0, 0, 0, 0, 0, 0); }
    public BoundingBox(double x1, double y1, double z1, double x2, double y2, double z2) {
        minX = Math.min(x1, x2); minY = Math.min(y1, y2); minZ = Math.min(z1, z2);
        maxX = Math.max(x1, x2); maxY = Math.max(y1, y2); maxZ = Math.max(z1, z2);
    }
    public double getMinX() { return minX; }
    public double getMinY() { return minY; }
    public double getMinZ() { return minZ; }
    public double getMaxX() { return maxX; }
    public double getMaxY() { return maxY; }
    public double getMaxZ() { return maxZ; }
}
