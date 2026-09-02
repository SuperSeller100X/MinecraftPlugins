package org.bukkit.plugin.java;
import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
public abstract class JavaPlugin implements Plugin {
    private final FileConfiguration config = new YamlConfiguration();
    public abstract void onEnable();
    public abstract void onDisable();
    public File getDataFolder() { return new File("target/test-data/RapidHoppers"); }
    public InputStream getResource(String filename) { return null; }
    public void saveResource(String path, boolean replace) {}
    public void saveDefaultConfig() {}
    public void saveConfig() {}
    public void reloadConfig() {}
    public FileConfiguration getConfig() { return config; }
    public Server getServer() { return Bukkit.getServer(); }
    public Logger getLogger() { return Logger.getLogger("RapidHoppers"); }
    public String getName() { return "RapidHoppers"; }
    public PluginCommand getCommand(String name) { return new PluginCommand(name); }
}
