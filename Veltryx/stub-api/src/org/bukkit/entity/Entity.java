package org.bukkit.entity;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

public interface Entity {
    UUID getUniqueId();
    Location getLocation();
    World getWorld();
    BoundingBox getBoundingBox();
    boolean isDead();
    boolean isInsideVehicle();
    boolean isGliding();
    boolean isRiptiding();
    float getFallDistance();
    Server getServer();
}
