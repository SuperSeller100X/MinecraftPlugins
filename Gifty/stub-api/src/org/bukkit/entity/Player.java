package org.bukkit.entity;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;

public interface Player extends HumanEntity, OfflinePlayer, CommandSender {
    void playSound(Location location, Sound sound, float volume, float pitch);
}
