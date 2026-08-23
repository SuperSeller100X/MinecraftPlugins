package org.bukkit;
public enum Material {
    AIR, CAVE_AIR, VOID_AIR, STONE, LAVA, FIRE, WATER;
    public boolean isSolid() { return this == STONE; }
}
