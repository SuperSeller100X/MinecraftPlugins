package org.bukkit.entity;
import org.bukkit.Location;
import org.bukkit.World;
public interface Entity {
    Location getLocation();
    World getWorld();
    boolean isDead();
    void remove();
}
