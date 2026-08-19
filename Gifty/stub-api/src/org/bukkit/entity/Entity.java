package org.bukkit.entity;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;

public interface Entity {
    UUID getUniqueId();
    Location getLocation();
    World getWorld();
}
