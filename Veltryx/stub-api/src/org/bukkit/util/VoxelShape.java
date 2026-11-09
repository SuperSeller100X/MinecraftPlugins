package org.bukkit.util;

import java.util.Collection;

/** Compile/test-only counterpart of Bukkit's block-local collision shape. */
public interface VoxelShape {
    Collection<BoundingBox> getBoundingBoxes();
    boolean overlaps(BoundingBox other);
}
