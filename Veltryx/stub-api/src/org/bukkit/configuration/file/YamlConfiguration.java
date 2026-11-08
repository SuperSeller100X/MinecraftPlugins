package org.bukkit.configuration.file;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;

/** Small test double for simple scalar YAML, not a replacement for Paper/SnakeYAML. */
public class YamlConfiguration extends FileConfiguration {
    private final Map<String, Object> values = new LinkedHashMap<>();
    public static YamlConfiguration loadConfiguration(File file) {
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            return loadConfiguration(reader);
        } catch (IOException e) { return new YamlConfiguration(); }
    }
    public static YamlConfiguration loadConfiguration(Reader reader) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            List<String> lines = new BufferedReader(reader).lines().toList();
            record Section(int indent, String path) { }
            Deque<Section> parents = new ArrayDeque<>();
            for (int i=0; i<lines.size(); i++) {
                String line=lines.get(i); if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
                int indent=line.length()-line.stripLeading().length();
                String trimmed=line.trim(); int colon=trimmed.indexOf(':');
                if (colon <= 0) continue;
                String key=trimmed.substring(0, colon); if (!key.matches("[a-zA-Z0-9_.-]+")) continue;
                while (!parents.isEmpty() && parents.peek().indent() >= indent) parents.pop();
                String path=parents.isEmpty() ? key : parents.peek().path()+"."+key;
                String value=trimmed.substring(colon+1).trim();
                if (value.isEmpty()) { parents.push(new Section(indent,path)); continue; }
                if (value.startsWith("|")) {
                    StringBuilder block = new StringBuilder();
                    while (i+1<lines.size()) {
                        String next=lines.get(i+1);
                        int nextIndent=next.length()-next.stripLeading().length();
                        if (!next.isBlank() && nextIndent<=indent) break;
                        i++; if (block.length()>0) block.append('\n'); block.append(next.stripLeading());
                    }
                    yaml.values.put(path, block.toString());
                } else if (value.startsWith("\"") && value.endsWith("\"") && value.length()>=2) {
                    yaml.values.put(path, value.substring(1,value.length()-1).replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\"));
                } else if (value.startsWith("'") && value.endsWith("'") && value.length()>=2) {
                    yaml.values.put(path, value.substring(1,value.length()-1).replace("''", "'"));
                } else if (value.equals("true") || value.equals("false")) yaml.values.put(path, Boolean.valueOf(value));
                else yaml.values.put(path, value);
            }
        } catch (Exception ignored) { }
        return yaml;
    }
    public void save(File file) throws IOException { }
    @Override public Set<String> getKeys(boolean deep) { return values.keySet(); }
    @Override public ConfigurationSection getConfigurationSection(String path) { return null; }
    @Override public String getString(String path, String def) { Object v=values.get(path); return v instanceof String ? (String)v : def; }
    @Override public String getString(String path) { return getString(path, null); }
    @Override public long getLong(String path, long def) { try { return Long.parseLong(String.valueOf(values.get(path))); } catch(Exception e) { return def; } }
    @Override public int getInt(String path, int def) { try { return Integer.parseInt(String.valueOf(values.get(path))); } catch(Exception e) { return def; } }
    @Override public double getDouble(String path, double def) { try { return Double.parseDouble(String.valueOf(values.get(path))); } catch(Exception e) { return def; } }
    @Override public boolean getBoolean(String path, boolean def) { Object v=values.get(path); return v instanceof Boolean ? (Boolean)v : def; }
    @Override public List<String> getStringList(String path) { return List.of(); }
    @Override public boolean isString(String path) { return values.get(path) instanceof String; }
    @Override public void set(String path, Object value) { values.put(path, value); }
    @Override public Map<String, Object> getValues(boolean deep) { return values; }
}
