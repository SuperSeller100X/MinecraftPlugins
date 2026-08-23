package org.bukkit.configuration.file;
import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.ConfigurationSection;
public abstract class FileConfiguration implements ConfigurationSection {
    public abstract void save(File file) throws IOException;
}
