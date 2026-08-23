package org.bukkit.event.block;
import org.bukkit.event.Cancellable;
public class BlockBurnEvent extends BlockEvent implements Cancellable {
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) {}
}
