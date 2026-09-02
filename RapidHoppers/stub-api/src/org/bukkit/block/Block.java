package org.bukkit.block;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
public interface Block {
    World getWorld();
    int getX();
    int getY();
    int getZ();
    Material getType();
    BlockData getBlockData();
    BlockState getState();
    BlockState getState(boolean useSnapshot);
    Block getRelative(int dx, int dy, int dz);
    Block getRelative(BlockFace face);
    Location getLocation();
}
