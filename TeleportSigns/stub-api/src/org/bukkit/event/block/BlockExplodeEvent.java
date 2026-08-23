package org.bukkit.event.block;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
public class BlockExplodeEvent extends BlockEvent implements Cancellable {
    public List<Block> blockList() { return new ArrayList<Block>(); }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) {}
}
