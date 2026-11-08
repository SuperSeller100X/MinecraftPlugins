package org.bukkit.entity;

import java.util.Collection;
import org.bukkit.potion.PotionEffect;

public interface LivingEntity extends Entity {
    Collection<PotionEffect> getActivePotionEffects();
    void addPotionEffect(PotionEffect effect);
    Player getKiller();
    double getHealth();
    int getFoodLevel();
    void setHealth(double health);
}
