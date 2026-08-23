package org.bukkit.entity;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
public interface Entity {
    UUID getUniqueId();
    Location getLocation();
    boolean teleport(Location location);
    CompletableFuture<Boolean> teleportAsync(Location location);
}
