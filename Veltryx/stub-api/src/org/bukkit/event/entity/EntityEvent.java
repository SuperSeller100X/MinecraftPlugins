package org.bukkit.event.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.Event;

public abstract class EntityEvent extends Event {
    public Entity getEntity() { return null; }
}
