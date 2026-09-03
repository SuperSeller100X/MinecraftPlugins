package org.bukkit;
import java.util.Collection;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
public interface Chunk {
    int getX();
    int getZ();
    World getWorld();
    Collection<BlockState> getTileEntities();
    Entity[] getEntities();
}
