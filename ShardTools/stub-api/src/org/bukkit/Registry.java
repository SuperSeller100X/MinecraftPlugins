package org.bukkit;
public interface Registry<T extends Keyed> {
    T get(NamespacedKey key);
}
