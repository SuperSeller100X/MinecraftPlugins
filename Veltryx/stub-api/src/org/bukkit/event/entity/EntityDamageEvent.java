package org.bukkit.event.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

public class EntityDamageEvent extends Event implements Cancellable {
    public Entity getEntity() { return null; }
    public DamageCause getCause() { return DamageCause.CUSTOM; }
    public double getFinalDamage() { return 0; }
    public enum DamageCause {
        CONTACT, ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, PROJECTILE, SUFFOCATION, FALL, FIRE, FIRE_TICK,
        MELTING, LAVA, DROWNING, BLOCK_EXPLOSION, ENTITY_EXPLOSION, VOID, LIGHTNING, SUICIDE,
        STARVATION, POISON, MAGIC, WITHER, FALLING_BLOCK, THORNS, DRAGON_BREATH, CUSTOM,
        FLY_INTO_WALL, HOT_FLOOR, CRAMMING, FREEZE, SONIC_BOOM, KILL, WORLD_BORDER, DRYOUT, SUNBURN
    }
    @Override public void setCancelled(boolean cancelled) {}
    @Override public boolean isCancelled() { return false; }
}
