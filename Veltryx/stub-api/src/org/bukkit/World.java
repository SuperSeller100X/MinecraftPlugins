package org.bukkit;

import org.bukkit.block.Block;

public interface World {
    Block getBlockAt(int x, int y, int z);
    <T> T spawn(Location location, Class<T> clazz, java.util.function.Consumer<T> function);
    void spawnParticle(Particle particle, double x, double y, double z, int count);
    org.bukkit.entity.Item dropItemNaturally(Location location, org.bukkit.inventory.ItemStack stack);
    boolean hasStorm();
    boolean isChunkLoaded(int x, int z);
    int getMinHeight();
    int getMaxHeight();
    int getHighestBlockYAt(int x, int z);
    Environment getEnvironment();
    enum Environment { NORMAL, NETHER, THE_END, CUSTOM }
    String getName();
    java.util.UUID getUID();
    org.bukkit.util.StructureSearchResult locateNearestStructure(Location origin, org.bukkit.generator.structure.Structure structure, int radius, boolean unexplored);
}
