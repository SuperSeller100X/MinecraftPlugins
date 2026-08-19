package org.bukkit.plugin.java;

import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.plugin.PluginBase;

public abstract class JavaPlugin extends PluginBase {
    public abstract void onEnable();

    public abstract void onDisable();

    public File getDataFolder() {
        return new File("target/test-data/Gifty");
    }

    public InputStream getResource(String filename) {
        try {
            File f = new File("src/main/resources", filename);
            if (f.exists()) {
                return new java.io.FileInputStream(f);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public Server getServer() {
        return org.bukkit.Bukkit.getServer();
    }

    public Logger getLogger() {
        return Logger.getLogger("Gifty");
    }

    public String getName() {
        return "Gifty";
    }
}
