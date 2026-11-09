package com.destroystokyo.paper.event.entity;

import org.bukkit.entity.Entity;

public class PhantomPreSpawnEvent extends PreCreatureSpawnEvent {
    /** The player the phantom is spawning for. */
    public Entity getSpawningEntity() { return null; }
}
