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
    BlockState getState();
    void setType(Material type);
    boolean breakNaturally();
    boolean breakNaturally(org.bukkit.inventory.ItemStack tool, boolean triggerEffect);
    World getWorld();
    int getX();
    int getY();
    int getZ();
}
