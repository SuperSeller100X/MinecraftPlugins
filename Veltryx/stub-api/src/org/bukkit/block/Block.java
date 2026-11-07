package org.bukkit.block;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

public interface Block {
    Material getType();
    Block getRelative(BlockFace face);
    Location getLocation();
    BlockData getBlockData();
    World getWorld();
    int getX();
    int getY();
    int getZ();
}
