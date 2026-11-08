package org.bukkit.inventory.meta;

public interface ArrowMeta extends ItemMeta {
    void setBasePotionType(org.bukkit.potion.PotionType type);
    org.bukkit.potion.PotionType getBasePotionType();
}
