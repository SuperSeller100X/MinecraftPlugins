package org.bukkit.configuration;
import java.util.List;
import java.util.Set;
public interface ConfigurationSection {
    default String getString(String path) { return null; }
    default int getInt(String path) { return 0; }
    default long getLong(String path) { return 0L; }
    default boolean getBoolean(String path) { return false; }
    default double getDouble(String path) { return 0D; }
    default double getDouble(String path, double def) { return def; }
    default String getString(String path, String def) { return def; }
    default List<String> getStringList(String path) { return List.of(); }
    default List<Long> getLongList(String path) { return List.of(); }
    default int getInt(String path, int def) { return def; }
    default long getLong(String path, long def) { return def; }
    default boolean getBoolean(String path, boolean def) { return def; }
    default boolean contains(String path) { return false; }
    default ConfigurationSection getConfigurationSection(String path) { return null; }
    default ConfigurationSection createSection(String path) { return null; }
    default Set<String> getKeys(boolean deep) { return Set.of(); }
    default void set(String path, Object value) {}
    default void setDefaults(ConfigurationSection defaults) {}
}
