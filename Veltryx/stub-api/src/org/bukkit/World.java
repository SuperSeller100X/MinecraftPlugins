package org.bukkit;

import org.bukkit.block.Block;

public interface World {
    Block getBlockAt(int x, int y, int z);
    boolean isChunkLoaded(int x, int z);
    int getMinHeight();
    int getMaxHeight();
    String getName();
}
