package dev.superseller.chunkvoter.vote;

import org.bukkit.World;

/**
 * Immutable, hashable identity for a chunk. Uses the world name rather than a
 * {@link World} reference so it is safe to store across reloads.
 */
public record ChunkKey(String world, int x, int z) {

    public static ChunkKey of(World world, int x, int z) {
        return new ChunkKey(world.getName(), x, z);
    }

    public static ChunkKey of(String world, int x, int z) {
        return new ChunkKey(world, x, z);
    }

    @Override
    public String toString() {
        return world + " (" + x + ", " + z + ")";
    }
}
