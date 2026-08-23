package org.bukkit.configuration.file;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
public class YamlConfiguration extends FileConfiguration {
    private final Map<String, Object> data = new HashMap<String, Object>();
    public static YamlConfiguration loadConfiguration(File file) { return new YamlConfiguration(); }
    public void save(File file) throws IOException {}
    public Set<String> getKeys(boolean deep) { return data.keySet(); }
    public String getString(String path) { return getString(path, null); }
    public String getString(String path, String def) {
        Object v = data.get(path); return v == null ? def : String.valueOf(v);
    }
    public int getInt(String path) { return getInt(path, 0); }
    public int getInt(String path, int def) {
        Object v = data.get(path); return v instanceof Number ? ((Number) v).intValue() : def;
    }
    public double getDouble(String path, double def) {
        Object v = data.get(path); return v instanceof Number ? ((Number) v).doubleValue() : def;
    }
    public boolean getBoolean(String path, boolean def) {
        Object v = data.get(path); return v instanceof Boolean ? ((Boolean) v).booleanValue() : def;
    }
    @SuppressWarnings("unchecked")
    public List<String> getStringList(String path) {
        Object v = data.get(path);
        return v instanceof List ? (List<String>) v : Collections.<String>emptyList();
    }
    public boolean isConfigurationSection(String path) { return false; }
    public ConfigurationSection getConfigurationSection(String path) { return this; }
    public void set(String path, Object value) { data.put(path, value); }
}
