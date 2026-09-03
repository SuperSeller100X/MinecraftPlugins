package org.bukkit.configuration;
import java.util.List;
public interface ConfigurationSection {
    boolean contains(String path);
    Object get(String path);
    void set(String path, Object value);
    String getString(String path, String def);
    int getInt(String path, int def);
    double getDouble(String path, double def);
    boolean getBoolean(String path, boolean def);
    List<String> getStringList(String path);
}
