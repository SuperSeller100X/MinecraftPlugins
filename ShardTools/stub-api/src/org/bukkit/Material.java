package org.bukkit;
public enum Material {
    AIR, DIRT, GRASS_BLOCK,
    CHEST, TRAPPED_CHEST, BARREL, HOPPER, DISPENSER, DROPPER,
    FURNACE, BLAST_FURNACE, SMOKER, BREWING_STAND, SHULKER_BOX,
    TOTEM_OF_UNDYING;

    public static Material matchMaterial(String name) { return AIR; }
    public int getMaxStackSize() { return 64; }
    public boolean isSolid() { return true; }
}
