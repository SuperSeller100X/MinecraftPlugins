package org.bukkit.entity;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

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
    void setVelocity(Vector velocity);
    Vector getVelocity();
    Server getServer();
    boolean teleport(Location location);
    boolean isSneaking();
    boolean isInWater();
}
