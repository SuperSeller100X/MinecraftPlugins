package com.destroystokyo.paper.event.entity;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

public class PreCreatureSpawnEvent extends Event implements Cancellable {
    @Override public boolean isCancelled() { return false; }
    @Override public void setCancelled(boolean cancel) { }
}
