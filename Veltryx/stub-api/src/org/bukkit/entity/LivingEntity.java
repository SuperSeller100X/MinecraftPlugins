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
    int getFoodLevel();
    void setHealth(double health);
}
