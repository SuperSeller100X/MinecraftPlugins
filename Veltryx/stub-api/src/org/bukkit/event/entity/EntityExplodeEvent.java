package org.bukkit.event.entity;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
public class EntityExplodeEvent extends Event implements Cancellable {
    public List<Block> blockList(){return null;}
    public void setCancelled(boolean value){} public boolean isCancelled(){return false;}
}
