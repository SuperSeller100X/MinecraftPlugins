package org.bukkit.event.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;

public class EntityTargetEvent extends EntityEvent implements Cancellable {
    public Entity getTarget() { return null; }
    public void setTarget(Entity target) { }
    public TargetReason getReason() { return null; }
    @Override public boolean isCancelled() { return false; }
    @Override public void setCancelled(boolean cancel) { }

    public enum TargetReason {
        CUSTOM
    }
}
