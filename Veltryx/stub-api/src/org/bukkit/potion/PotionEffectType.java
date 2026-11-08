package org.bukkit.potion;
public class PotionEffectType implements org.bukkit.Keyed {
    @Override
    public org.bukkit.NamespacedKey getKey() { return null; }
    public static final PotionEffectType SPEED = new PotionEffectType();
    public static final PotionEffectType SLOW = new PotionEffectType();
    public static final PotionEffectType JUMP_BOOST = new PotionEffectType();
    public static final PotionEffectType LEVITATION = new PotionEffectType();
    public static final PotionEffectType SLOW_FALLING = new PotionEffectType();
    public static final PotionEffectType HASTE = new PotionEffectType();
    public static final PotionEffectType RESISTANCE = new PotionEffectType();
    public static final PotionEffectType REGEN = new PotionEffectType();
    public static final PotionEffectType NIGHT_VISION = new PotionEffectType();
    public static final PotionEffectType INVISIBILITY = new PotionEffectType();
}
