package org.bukkit.event.block;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
public abstract class BlockEvent extends Event {
    public Block getBlock() { return null; }
}
