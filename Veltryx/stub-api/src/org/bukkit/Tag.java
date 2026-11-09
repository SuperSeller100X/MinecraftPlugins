package org.bukkit;
public interface Tag<T> {
    boolean isTagged(T item);
    Tag<Material> LOGS = m -> m != null && (m.name().endsWith("_LOG") || m.name().endsWith("_WOOD") || m.name().endsWith("_STEM") || m.name().endsWith("_HYPHAE"));
    Tag<Material> CLIMBABLE = m -> m == Material.LADDER || m == Material.VINE || m == Material.SCAFFOLDING
            || m == Material.TWISTING_VINES || m == Material.TWISTING_VINES_PLANT
            || m == Material.WEEPING_VINES || m == Material.WEEPING_VINES_PLANT
            || m == Material.CAVE_VINES || m == Material.CAVE_VINES_PLANT;
    Tag<Material> LEAVES = m -> m != null && m.name().endsWith("_LEAVES");
}
