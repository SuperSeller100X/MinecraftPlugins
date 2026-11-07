package org.bukkit.entity;

import java.util.Collection;
import org.bukkit.potion.PotionEffect;

public interface LivingEntity extends Entity {
    Collection<PotionEffect> getActivePotionEffects();
}
