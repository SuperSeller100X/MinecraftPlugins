package org.bukkit.event.entity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.Event;
public class EntityPickupItemEvent extends Event {
    public Entity getEntity() { return null; }
    public Item getItem() { return null; }
    public void setCancelled(boolean cancelled) {}
}
