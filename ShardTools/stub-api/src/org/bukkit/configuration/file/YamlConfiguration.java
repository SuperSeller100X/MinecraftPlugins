package org.bukkit.configuration.file;
import java.io.File;
import java.io.IOException;
public class YamlConfiguration implements FileConfiguration {
    public static YamlConfiguration loadConfiguration(File file) { return new YamlConfiguration(); }
    public void load(File file) throws IOException, org.bukkit.configuration.InvalidConfigurationException {}
    public void save(File file) throws IOException {}
}
