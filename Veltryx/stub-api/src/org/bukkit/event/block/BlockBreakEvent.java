package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

public class BlockBreakEvent extends Event implements Cancellable {
    public Player getPlayer() { return null; }
    public Block getBlock() { return null; }
    @Override public void setCancelled(boolean cancelled) {}
    @Override public boolean isCancelled() { return false; }
}
