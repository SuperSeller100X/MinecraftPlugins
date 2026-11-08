package org.bukkit.entity;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public interface Entity {
    UUID getUniqueId();
    EntityType getType();
    org.bukkit.persistence.PersistentDataContainer getPersistentDataContainer();
    Location getLocation();
    World getWorld();
    BoundingBox getBoundingBox();
    boolean isDead();
    boolean isInsideVehicle();
    boolean isGliding();
    boolean isRiptiding();
    float getFallDistance();
    default void setFallDistance(float value) {}
    default boolean isOnGround() { return false; }
    default void playEffect(org.bukkit.EntityEffect effect) {}
    void setVelocity(Vector velocity);
    Vector getVelocity();
    Server getServer();
    boolean teleport(Location location);
    boolean isSneaking();
    boolean isInWater();
    default boolean isInRain() { return false; }
    default void setInvulnerable(boolean value) {}
}
