package org.bukkit.event.block;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
public class BlockExplodeEvent extends Event implements Cancellable {
    public List<Block> blockList(){return null;}
    public void setCancelled(boolean value){} public boolean isCancelled(){return false;}
}
