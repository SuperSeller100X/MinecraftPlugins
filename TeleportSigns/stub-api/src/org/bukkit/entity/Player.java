package org.bukkit.entity;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
public interface Player extends Entity, OfflinePlayer, CommandSender {
    boolean isOnline();
    boolean isSneaking();
    World getWorld();
    Block getTargetBlockExact(int maxDistance);
    Block getTargetBlockExact(int maxDistance, FluidCollisionMode mode);
    void playSound(Location location, Sound sound, float volume, float pitch);
    void spawnParticle(Particle particle, Location location, int count, double ox, double oy, double oz, double extra);
}
