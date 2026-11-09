package org.bukkit.event.world;

import org.bukkit.Chunk;
import org.bukkit.event.Event;

/** Compile stub. */
public abstract class ChunkEvent extends Event {
    protected final Chunk chunk;

    public ChunkEvent(Chunk chunk) {
        this.chunk = chunk;
    }

    public Chunk getChunk() {
        return chunk;
    }
}
