package org.bukkit.inventory.meta;
import org.bukkit.Color;
public interface PotionMeta extends ItemMeta {
    void setColor(Color color);
    boolean addCustomEffect(org.bukkit.potion.PotionEffect effect, boolean override);
    boolean hasCustomEffects();
    java.util.List<org.bukkit.potion.PotionEffect> getCustomEffects();
    void setBasePotionType(org.bukkit.potion.PotionType type);
    org.bukkit.potion.PotionType getBasePotionType();
}
