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
    int getMinHeight();
    Location getSpawnLocation();
    <T extends Entity> T spawn(Location location, Class<T> type, Consumer<T> consumer);
    Item dropItem(Location location, ItemStack stack);
    void spawnParticle(Particle particle, Location location, int count,
                       double offsetX, double offsetY, double offsetZ, double extra);
}
