package org.bukkit.plugin;

import org.bukkit.event.Listener;

/**
 * Compile-only stub — never shipped in the plugin jar.
 */
public interface PluginManager {

    void registerEvents(Listener listener, Plugin plugin);
}
