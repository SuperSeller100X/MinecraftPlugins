package org.bukkit.configuration.file;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
public class FileConfiguration implements ConfigurationSection {
    protected final Map<String, Object> values = new HashMap<>();
    public boolean contains(String path) { return values.containsKey(path); }
    public Object get(String path) { return values.get(path); }
    public void set(String path, Object value) { values.put(path, value); }
    public String getString(String path, String def) {
        Object v = values.get(path); return v == null ? def : String.valueOf(v);
    }
    public int getInt(String path, int def) {
        Object v = values.get(path); return v instanceof Number n ? n.intValue() : def;
    }
    public double getDouble(String path, double def) {
        Object v = values.get(path); return v instanceof Number n ? n.doubleValue() : def;
    }
    public boolean getBoolean(String path, boolean def) {
        Object v = values.get(path); return v instanceof Boolean b ? b : def;
    }
    @SuppressWarnings("unchecked")
    public List<String> getStringList(String path) {
        Object v = values.get(path);
        return v instanceof List<?> list ? (List<String>) list : new ArrayList<String>();
    }
}
