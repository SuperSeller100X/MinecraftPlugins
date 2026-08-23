package org.bukkit.persistence;
import org.bukkit.NamespacedKey;
public interface PersistentDataContainer {
    <T, Z> void set(NamespacedKey key, PersistentDataType<T, Z> type, Z value);
    <T, Z> Z get(NamespacedKey key, PersistentDataType<T, Z> type);
    <T, Z> boolean has(NamespacedKey key, PersistentDataType<T, Z> type);
    void remove(NamespacedKey key);
}
