package org.bukkit.plugin;

import java.util.logging.Logger;
import org.bukkit.Server;

public interface Plugin {
    Server getServer();
    Logger getLogger();
}
