package dev.superseller.randomstructurechallenge.challenge;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * After a vanilla structure overwrites the player, make sure they can breathe.
 */
public final class SafePocket {

    private SafePocket() {
    }

    public static void ensure(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        if (isSafe(loc)) {
            return;
        }
        Location nearby = findNearby(loc, 6);
        if (nearby != null) {
            teleport(player, nearby);
            return;
        }
        carve(world, loc);
        teleport(player, centered(loc));
    }

    private static boolean isSafe(Location loc) {
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        Block floor = loc.clone().subtract(0, 1, 0).getBlock();
        return passable(feet) && passable(head) && !dangerous(feet) && !dangerous(head)
                && !passable(floor) && !dangerous(floor);
    }

    private static Location findNearby(Location origin, int radius) {
        World world = origin.getWorld();
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.abs(dx) != r && Math.abs(dy) != r && Math.abs(dz) != r) {
                            continue;
                        }
                        int y = oy + dy;
                        if (y < minY || y > maxY) {
                            continue;
                        }
                        Location candidate = new Location(world, ox + dx + 0.5, y, oz + dz + 0.5,
                                origin.getYaw(), origin.getPitch());
                        if (isSafe(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static void carve(World world, Location loc) {
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        if (y < minY + 1) {
            y = minY + 1;
        }
        if (y > maxY - 2) {
            y = maxY - 2;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int yy = y + dy;
                    if (yy < minY || yy > maxY) {
                        continue;
                    }
                    Block block = world.getBlockAt(x + dx, yy, z + dz);
                    if (!passable(block) || dangerous(block)) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
        Block floor = world.getBlockAt(x, y - 1, z);
        if (passable(floor) || dangerous(floor)) {
            floor.setType(Material.GLASS, false);
        }
        loc.setY(y);
    }

    private static Location centered(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX() + 0.5, loc.getY(), loc.getBlockZ() + 0.5,
                loc.getYaw(), loc.getPitch());
    }

    private static void teleport(Player player, Location dest) {
        try {
            player.teleportAsync(dest);
        } catch (Throwable ignored) {
            player.teleport(dest);
        }
    }

    private static boolean passable(Block block) {
        return block.isPassable() && block.getType() != Material.COBWEB;
    }

    private static boolean dangerous(Block block) {
        Material type = block.getType();
        return type == Material.LAVA
                || type == Material.FIRE
                || type == Material.SOUL_FIRE
                || type == Material.WITHER_ROSE
                || type.name().contains("POWDER_SNOW");
    }
}
