package org.bukkit.event.block;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
public class BlockBreakEvent extends Event {
    public Player getPlayer() { return null; }
    public Block getBlock() { return null; }
}
