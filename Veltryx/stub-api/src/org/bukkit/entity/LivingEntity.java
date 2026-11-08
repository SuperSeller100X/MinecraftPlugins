package org.bukkit.entity;

import java.util.Collection;
import org.bukkit.potion.PotionEffect;

public interface LivingEntity extends Entity {
    Collection<PotionEffect> getActivePotionEffects();
    PotionEffect getPotionEffect(org.bukkit.potion.PotionEffectType type);
    boolean addPotionEffect(PotionEffect effect);
    void removePotionEffect(org.bukkit.potion.PotionEffectType type);
    Player getKiller();
    double getHealth();
    default double getAbsorptionAmount() { return 0; }
    default void setAbsorptionAmount(double value) {}
    default org.bukkit.attribute.AttributeInstance getAttribute(org.bukkit.attribute.Attribute value) { return null; }
    default void damageItemStack(org.bukkit.inventory.EquipmentSlot slot, int amount) {}
    int getFoodLevel();
    void setHealth(double health);
}
