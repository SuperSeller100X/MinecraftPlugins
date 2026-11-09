package org.bukkit.event.entity;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
public class SpawnerSpawnEvent extends Event implements Cancellable {
    public CreatureSpawner getSpawner(){return null;}
    public Entity getEntity(){return null;}
    public void setCancelled(boolean value){} public boolean isCancelled(){return false;}
}
