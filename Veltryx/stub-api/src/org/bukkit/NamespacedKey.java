package org.bukkit;
import org.bukkit.plugin.Plugin;
public class NamespacedKey {
    public NamespacedKey(Plugin plugin, String key) {}
    public static NamespacedKey minecraft(String key) { return new NamespacedKey(null, key); }
    public static NamespacedKey fromString(String string) { return null; }
    @Override
    public String toString() { return ""; }
}
