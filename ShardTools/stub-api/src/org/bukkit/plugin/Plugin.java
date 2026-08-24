package org.bukkit.plugin;
import java.io.File;
import java.util.logging.Logger;
import org.bukkit.Server;
public interface Plugin {
    Server getServer();
    File getDataFolder();
    Logger getLogger();
    String getName();
}
