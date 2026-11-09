package dev.superseller.veltryx.model;

import java.util.UUID;

/**
 * A named home location of one player.
 *
 * @param name  lower-case home name (unique per player)
 * @param world world name
 */
public record HomeLocation(UUID owner, String name, String world,
                           double x, double y, double z, float yaw, float pitch) {
}
