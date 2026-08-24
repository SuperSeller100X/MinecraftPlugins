package org.bukkit.plugin.java;
import java.io.File;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
public class JavaPlugin implements Plugin {
    public void onEnable() {}
    public void onDisable() {}
    public FileConfiguration getConfig() { return null; }
    public void reloadConfig() {}
    public void saveDefaultConfig() {}
    public void saveResource(String resourcePath, boolean replace) {}
    public java.io.InputStream getResource(String resourcePath) { return null; }
    public File getDataFolder() { return null; }
    public Logger getLogger() { return Logger.getLogger("ShardTools"); }
    public Server getServer() { return null; }
    public PluginCommand getCommand(String name) { return null; }
    public String getName() { return "ShardTools"; }
}
