package org.bukkit.block;
public enum BlockFace {
    NORTH(0, 0, -1), EAST(1, 0, 0), SOUTH(0, 0, 1), WEST(-1, 0, 0), UP(0, 1, 0), DOWN(0, -1, 0);
    private final int x, y, z;
    BlockFace(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    public int getModX() { return x; }
    public int getModY() { return y; }
    public int getModZ() { return z; }
}
