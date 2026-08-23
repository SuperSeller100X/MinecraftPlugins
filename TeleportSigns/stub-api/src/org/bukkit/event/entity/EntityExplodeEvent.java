package org.bukkit.event.entity;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
public class EntityExplodeEvent extends EntityEvent implements Cancellable {
    public List<Block> blockList() { return new ArrayList<Block>(); }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) {}
}
