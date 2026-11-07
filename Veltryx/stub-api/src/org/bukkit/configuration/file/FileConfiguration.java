package org.bukkit.configuration.file;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

public abstract class FileConfiguration implements ConfigurationSection {
    public abstract Set<String> getKeys(boolean deep);
    public abstract ConfigurationSection getConfigurationSection(String path);
    public abstract String getString(String path, String def);
    public abstract String getString(String path);
    public abstract long getLong(String path, long def);
    public abstract int getInt(String path, int def);
    public abstract double getDouble(String path, double def);
    public abstract boolean getBoolean(String path, boolean def);
    public abstract List<String> getStringList(String path);
    public abstract boolean isString(String path);
    public abstract void set(String path, Object value);
    public abstract Map<String, Object> getValues(boolean deep);
}
