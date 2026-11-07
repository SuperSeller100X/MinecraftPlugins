package org.bukkit.event.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

public class EntityDamageEvent extends Event implements Cancellable {
    public Entity getEntity() { return null; }
    @Override public void setCancelled(boolean cancelled) {}
    @Override public boolean isCancelled() { return false; }
}
