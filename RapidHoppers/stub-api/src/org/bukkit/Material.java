package org.bukkit;
public enum Material {
    AIR, HOPPER, BARRIER, CLOCK, CHEST, MINECART, REDSTONE_TORCH, IRON_BARS, MAP, PAPER,
    COMPARATOR, RED_STAINED_GLASS_PANE, GRAY_STAINED_GLASS_PANE, STONE, DROPPER, DISPENSER;
    public boolean isAir() { return this == AIR; }
    public static Material matchMaterial(String name) {
        try { return valueOf(name); } catch (IllegalArgumentException ex) { return null; }
    }
}
