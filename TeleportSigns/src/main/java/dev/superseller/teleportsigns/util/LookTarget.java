package dev.superseller.teleportsigns.util;

import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

/** Ray-traces the sign a player is looking at. */
public final class LookTarget {

    private LookTarget() {
    }

    public static Block block(Player player, int maxDistance) {
        if (player == null) {
            return null;
        }
        int distance = Math.max(1, maxDistance);
        try {
            return player.getTargetBlockExact(distance, FluidCollisionMode.NEVER);
        } catch (Throwable ignored) {
            return player.getTargetBlockExact(distance);
        }
    }

    public static Sign sign(Player player, int maxDistance) {
        Block block = block(player, maxDistance);
        if (block == null) {
            return null;
        }
        if (!(block.getState() instanceof Sign)) {
            return null;
        }
        return (Sign) block.getState();
    }

    public static boolean isSign(Block block) {
        if (block == null) {
            return false;
        }
        return block.getState() instanceof Sign;
    }
}
