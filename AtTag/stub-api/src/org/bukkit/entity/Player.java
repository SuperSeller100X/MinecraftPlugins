package org.bukkit.entity;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.Sound;

/**
 * Compile-only stub — never shipped in the plugin jar.
 */
public interface Player {

    String getName();

    Server getServer();

    Location getLocation();

    boolean isOnline();

    void playSound(Location location, Sound sound, float volume, float pitch);
}
