package org.bukkit.event.entity;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
public class EntityDamageEvent extends Event {
    public enum DamageCause { VOID, FALL, FIRE, DROWNING, ENTITY_ATTACK }
    public Entity getEntity() { return null; }
    public DamageCause getCause() { return null; }
    public double getFinalDamage() { return 0D; }
    public void setCancelled(boolean cancelled) {}
    public boolean isCancelled() { return false; }
}
