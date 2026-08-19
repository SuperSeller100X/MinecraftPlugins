package org.bukkit.plugin;

import java.io.File;
import java.util.logging.Logger;
import org.bukkit.Server;

/**
 * Compile-only stub of the Bukkit Plugin interface.
 * Mirrors the real API signatures used by Gifty so the plugin can be compiled
 * without the official server jars (see README "Building" section).
 */
public interface Plugin {
    Server getServer();
    Logger getLogger();
    String getName();
    File getDataFolder();
    boolean isEnabled();
}
