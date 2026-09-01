package org.bukkit.plugin;
import org.bukkit.event.Listener;
public interface PluginManager {
    void registerEvents(Listener listener, Plugin plugin);
    Plugin getPlugin(String name);
}
