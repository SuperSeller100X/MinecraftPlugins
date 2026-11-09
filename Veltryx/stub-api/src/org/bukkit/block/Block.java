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
    default org.bukkit.util.VoxelShape getCollisionShape() {
        return new org.bukkit.util.VoxelShape() {
            public java.util.Collection<org.bukkit.util.BoundingBox> getBoundingBoxes() { return java.util.List.of(); }
            public boolean overlaps(org.bukkit.util.BoundingBox other) { return false; }
        };
    }
    BlockState getState();
    void setType(Material type);
    boolean breakNaturally();
    boolean breakNaturally(org.bukkit.inventory.ItemStack tool, boolean triggerEffect);
    World getWorld();
    int getX();
    int getY();
    int getZ();
}
