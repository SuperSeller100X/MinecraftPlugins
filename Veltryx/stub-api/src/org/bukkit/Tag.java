package org.bukkit;
public interface Tag<T> {
    boolean isTagged(T item);
    Tag<Material> LOGS = m -> m != null && (m.name().endsWith("_LOG") || m.name().endsWith("_WOOD") || m.name().endsWith("_STEM") || m.name().endsWith("_HYPHAE"));
    Tag<Material> LEAVES = m -> m != null && m.name().endsWith("_LEAVES");
}
