package org.bukkit;

import org.bukkit.block.Block;

public interface World {
    Block getBlockAt(int x, int y, int z);
    <T> T spawn(Location location, Class<T> clazz, java.util.function.Consumer<T> function);
    void spawnParticle(Particle particle, double x, double y, double z, int count);
    boolean hasStorm();
    boolean isChunkLoaded(int x, int z);
    int getMinHeight();
    int getMaxHeight();
    String getName();
}
