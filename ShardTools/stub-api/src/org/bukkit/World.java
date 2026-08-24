package org.bukkit;
import java.util.function.Consumer;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
public interface World {
    String getName();
    Block getBlockAt(int x, int y, int z);
    boolean isChunkLoaded(int chunkX, int chunkZ);
    <T extends Entity> T spawn(Location location, Class<T> type, Consumer<T> consumer);
    Item dropItem(Location location, ItemStack stack);
}
