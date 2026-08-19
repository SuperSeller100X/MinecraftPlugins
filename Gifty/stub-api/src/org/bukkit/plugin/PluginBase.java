package org.bukkit.plugin;

import java.io.File;
import java.util.logging.Logger;
import org.bukkit.Server;

public abstract class PluginBase implements Plugin {
    @Override
    public abstract Server getServer();

    @Override
    public abstract Logger getLogger();

    @Override
    public abstract String getName();

    @Override
    public abstract File getDataFolder();

    @Override
    public boolean isEnabled() {
        return true;
    }
}
