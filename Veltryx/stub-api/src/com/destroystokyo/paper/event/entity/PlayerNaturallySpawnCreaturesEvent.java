package com.destroystokyo.paper.event.entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.player.PlayerEvent;
public class PlayerNaturallySpawnCreaturesEvent extends PlayerEvent implements Cancellable {
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) { }
}
