package org.bukkit.event.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

public class FoodLevelChangeEvent extends Event implements Cancellable {
    public Entity getEntity() { return null; }
    public int getFoodLevel() { return 0; }
    public void setFoodLevel(int foodLevel) {}
    @Override public void setCancelled(boolean cancelled) {}
    @Override public boolean isCancelled() { return false; }
}
