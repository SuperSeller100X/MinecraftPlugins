package org.bukkit.event.entity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
public class EntityPotionEffectEvent extends EntityEvent {
    public PotionEffectType getModifiedType() { return null; }
    public PotionEffect getNewEffect() { return null; }
}
