package org.bukkit.event.entity;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;

public class CreatureSpawnEvent extends EntityEvent implements Cancellable {
    @Override public LivingEntity getEntity() { return null; }
    public SpawnReason getSpawnReason() { return null; }
    @Override public boolean isCancelled() { return false; }
    @Override public void setCancelled(boolean cancel) { }

    public enum SpawnReason {
        NATURAL, CUSTOM
    }
}
