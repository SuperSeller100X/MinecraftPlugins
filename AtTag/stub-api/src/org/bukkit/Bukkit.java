package org.bukkit;

import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;

/**
 * Compile-only stub — never shipped in the plugin jar.
 */
public final class Bukkit {

    private static Server server;

    private Bukkit() {
    }

    /** Test hook — stubs are compile-only and never shipped. */
    public static void setServer(Server s) {
        server = s;
    }

    public static Server getServer() {
        return server;
    }

    public static PluginManager getPluginManager() {
        return server == null ? null : server.getPluginManager();
    }

    public static BukkitScheduler getScheduler() {
        return server == null ? null : server.getScheduler();
    }
}
