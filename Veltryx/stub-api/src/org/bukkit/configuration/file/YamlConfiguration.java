package org.bukkit.configuration.file;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;

/** Small test double for simple scalar YAML, not a replacement for Paper/SnakeYAML. */
public class YamlConfiguration extends FileConfiguration {
    private final Map<String, Object> values;
    private final String prefix;
    public YamlConfiguration() { this(new LinkedHashMap<>(), ""); }
    private YamlConfiguration(Map<String, Object> values, String prefix) { this.values=values; this.prefix=prefix; }
    private String path(String key) { return prefix + key; }
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
    public void load(File file) throws IOException, org.bukkit.configuration.InvalidConfigurationException {
        YamlConfiguration loaded=loadConfiguration(file); values.clear(); values.putAll(loaded.values);
    }
    public void save(File file) throws IOException {
        Map<String,Object> tree = new LinkedHashMap<>();
        for (var e : values.entrySet()) {
            String[] parts=e.getKey().split("\\."); Map<String,Object> node=tree;
            for(int i=0;i<parts.length-1;i++) node=(Map<String,Object>)node.computeIfAbsent(parts[i], k -> new LinkedHashMap<String,Object>());
            node.put(parts[parts.length-1],e.getValue());
        }
        try (Writer out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) { emit(out,tree,0); }
    }
    @SuppressWarnings("unchecked")
    private static void emit(Writer out, Map<String,Object> map, int indent) throws IOException {
        for(var e:map.entrySet()) {
            out.write(" ".repeat(indent)+e.getKey()+":"); Object v=e.getValue();
            if(v instanceof Map) { out.write("\n"); emit(out,(Map<String,Object>)v,indent+2); }
            else if(v instanceof String) out.write(" '"+((String)v).replace("'","''")+"'\n");
            else out.write(" "+String.valueOf(v)+"\n");
        }
    }
    @Override public Set<String> getKeys(boolean deep) {
        Set<String> result=new LinkedHashSet<>();
        for(String full:values.keySet()) if(full.startsWith(prefix)) {
            String rel=full.substring(prefix.length()); int dot=rel.indexOf('.');
            result.add(!deep && dot>=0 ? rel.substring(0,dot) : rel);
        }
        return result;
    }
    @Override public ConfigurationSection getConfigurationSection(String key) {
        String child=path(key)+".";
        for(String full:values.keySet()) if(full.startsWith(child)) return new YamlConfiguration(values,child);
        return null;
    }
    @Override public String getString(String key, String def) { Object v=values.get(path(key)); return v==null ? def : v.toString(); }
    @Override public String getString(String key) { return getString(key, null); }
    @Override public long getLong(String key, long def) { try { return Long.parseLong(String.valueOf(values.get(path(key)))); } catch(Exception e) { return def; } }
    @Override public int getInt(String key, int def) { try { return Integer.parseInt(String.valueOf(values.get(path(key)))); } catch(Exception e) { return def; } }
    @Override public double getDouble(String key, double def) { try { return Double.parseDouble(String.valueOf(values.get(path(key)))); } catch(Exception e) { return def; } }
    @Override public boolean getBoolean(String key, boolean def) { Object v=values.get(path(key)); return v instanceof Boolean ? (Boolean)v : def; }
    @Override public List<String> getStringList(String path) { return List.of(); }
    @Override public boolean isString(String key) { return values.get(path(key)) instanceof String; }
    @Override public void set(String key, Object value) { values.put(path(key), value); }
    @Override public Map<String, Object> getValues(boolean deep) { return values; }
}
