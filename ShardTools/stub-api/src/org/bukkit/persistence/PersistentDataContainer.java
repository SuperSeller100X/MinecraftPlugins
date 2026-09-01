package org.bukkit.persistence;
import org.bukkit.NamespacedKey;
public interface PersistentDataContainer {
    <T, Z> void set(NamespacedKey key, PersistentDataType<T, Z> type, Z value);
    <T, Z> Z get(NamespacedKey key, PersistentDataType<T, Z> type);
    <T, Z> T getOrDefault(NamespacedKey key, PersistentDataType<T, Z> type, T def);
    <T, Z> boolean has(NamespacedKey key, PersistentDataType<T, Z> type);
}
