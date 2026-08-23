package org.bukkit.plugin.java;
import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
public abstract class JavaPlugin implements Plugin {
    public abstract void onEnable();
    public abstract void onDisable();
    public File getDataFolder() { return new File("target/test-data/TeleportSigns"); }
    public InputStream getResource(String filename) { return null; }
    public void saveResource(String path, boolean replace) {}
    public void saveDefaultConfig() {}
    public void reloadConfig() {}
    public org.bukkit.configuration.file.FileConfiguration getConfig() {
        return new org.bukkit.configuration.file.YamlConfiguration();
    }
    public Server getServer() { return Bukkit.getServer(); }
    public Logger getLogger() { return Logger.getLogger("TeleportSigns"); }
    public String getName() { return "TeleportSigns"; }
    public PluginCommand getCommand(String name) { return new PluginCommand(name); }
}
