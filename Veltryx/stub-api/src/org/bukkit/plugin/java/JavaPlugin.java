package org.bukkit.plugin.java;

import java.io.File;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginMeta;

public abstract class JavaPlugin implements Plugin {
    public void onEnable() {}
    public void onDisable() {}
    public void saveDefaultConfig() {}
    public java.io.InputStream getResource(String name) { return null; }
    public void saveResource(String resourcePath, boolean replace) {}
    public FileConfiguration getConfig() { return null; }
    public void reloadConfig() {}
    public PluginCommand getCommand(String name) { return null; }
    public File getDataFolder() { return null; }
    @Override public Logger getLogger() { return null; }
    @Override public Server getServer() { return null; }
    public PluginMeta getPluginMeta() { return null; }
}
