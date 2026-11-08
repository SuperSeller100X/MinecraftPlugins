package org.bukkit;
import org.bukkit.plugin.Plugin;
public class NamespacedKey {
    private final String value;
    public NamespacedKey(Plugin plugin, String key) { this.value = (plugin == null ? "minecraft" : "veltryx") + ":" + key; }
    public static NamespacedKey minecraft(String key) { return new NamespacedKey(null, key); }
    private NamespacedKey(String value) { this.value = value; }
    public static NamespacedKey fromString(String string) { return new NamespacedKey(string.contains(":") ? string : "minecraft:" + string); }
    @Override public String toString() { return value; }
    @Override public boolean equals(Object other) { return other instanceof NamespacedKey k && value.equals(k.value); }
    @Override public int hashCode() { return value.hashCode(); }
}
