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
    default void startUsingItem(org.bukkit.inventory.EquipmentSlot hand) {}
    default boolean hasActiveItem() { return false; }
    default org.bukkit.inventory.EquipmentSlot getActiveItemHand() { return org.bukkit.inventory.EquipmentSlot.HAND; }
    default org.bukkit.inventory.ItemStack getActiveItem() { return null; }
    default void clearActiveItem() {}
    int getFoodLevel();
    void setHealth(double health);
}
