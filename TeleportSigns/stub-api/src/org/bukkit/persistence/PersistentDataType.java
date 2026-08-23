package org.bukkit.persistence;
public interface PersistentDataType<T, Z> {
    PersistentDataType<String, String> STRING = new PersistentDataType<String, String>() {};
}
