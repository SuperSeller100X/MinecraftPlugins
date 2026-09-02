package org.bukkit;
import java.util.Collection;
import java.util.List;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
public interface World {
    String getName();
    int getMinHeight();
    int getMaxHeight();
    Chunk[] getLoadedChunks();
    boolean isChunkLoaded(int x, int z);
    Chunk getChunkAt(int x, int z);
    List<Player> getPlayers();
    Collection<Entity> getNearbyEntities(Location location, double x, double y, double z);
}
