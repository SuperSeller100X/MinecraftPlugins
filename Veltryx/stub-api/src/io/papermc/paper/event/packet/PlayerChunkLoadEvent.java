package io.papermc.paper.event.packet;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.world.ChunkEvent;

/** Compile stub. Fired when a player's client receives a chunk. */
public class PlayerChunkLoadEvent extends ChunkEvent {
    private final Player player;

    public PlayerChunkLoadEvent(Chunk chunk, Player player) {
        super(chunk);
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }
}
