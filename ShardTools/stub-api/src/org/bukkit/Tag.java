package org.bukkit;
public interface Tag<T> {
    boolean isTagged(T item);
    Tag<Material> LOGS = null;
}
