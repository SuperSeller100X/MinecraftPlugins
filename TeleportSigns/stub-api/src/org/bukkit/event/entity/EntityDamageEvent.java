package org.bukkit.event.entity;
import org.bukkit.event.Cancellable;
public class EntityDamageEvent extends EntityEvent implements Cancellable {
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) {}
}
