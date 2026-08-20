package org.bukkit.plugin.java;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

/**
 * Compile-only stub — never shipped in the plugin jar.
 * The resource/data-folder methods are functional so offline tests can
 * exercise the real plugin against a mock server.
 */
public abstract class JavaPlugin implements Plugin {

    public File getDataFolder() {
        return new File("target/test-data/AtTag");
    }

    public InputStream getResource(String filename) {
        try {
            File f = new File("src/main/resources", filename);
            if (f.exists()) {
                return new FileInputStream(f);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    public Server getServer() {
        return org.bukkit.Bukkit.getServer();
    }

    public Logger getLogger() {
        return Logger.getLogger("AtTag");
    }

    public String getName() {
        return "AtTag";
    }
}
