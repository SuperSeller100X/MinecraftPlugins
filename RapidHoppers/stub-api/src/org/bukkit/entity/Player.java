package org.bukkit.entity;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
public interface Player extends HumanEntity, CommandSender {
    boolean isOnline();
    World getWorld();
    void playSound(Location location, Sound sound, float volume, float pitch);
}
