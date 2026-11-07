package org.bukkit.configuration.file;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

public class YamlConfiguration extends FileConfiguration {
    private final Map<String, Object> values = new HashMap<>();
    public static YamlConfiguration loadConfiguration(File file) { return new YamlConfiguration(); }
    public void save(File file) throws IOException {}
    @Override public Set<String> getKeys(boolean deep) { return values.keySet(); }
    @Override public ConfigurationSection getConfigurationSection(String path) { return null; }
    @Override public String getString(String path, String def) { return def; }
    @Override public String getString(String path) { return null; }
    @Override public long getLong(String path, long def) { return def; }
    @Override public int getInt(String path, int def) { return def; }
    @Override public double getDouble(String path, double def) { return def; }
    @Override public boolean getBoolean(String path, boolean def) { return def; }
    @Override public List<String> getStringList(String path) { return List.of(); }
    @Override public boolean isString(String path) { return false; }
    @Override public void set(String path, Object value) { values.put(path, value); }
    @Override public Map<String, Object> getValues(boolean deep) { return values; }
}
