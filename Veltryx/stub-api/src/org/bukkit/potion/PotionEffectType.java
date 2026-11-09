package org.bukkit.potion;
public class PotionEffectType implements org.bukkit.Keyed {
    private final String key;
    private final boolean instant;
    public PotionEffectType() { this("unknown", false); }
    public PotionEffectType(String key, boolean instant) { this.key=key; this.instant=instant; }
    @Override public org.bukkit.NamespacedKey getKey() { return org.bukkit.NamespacedKey.minecraft(key); }
    public boolean isInstant() { return instant; }
    public static final PotionEffectType SPEED = new PotionEffectType("speed", false);
    public static final PotionEffectType SLOW = new PotionEffectType("slowness", false);
    public static final PotionEffectType JUMP_BOOST = new PotionEffectType("jump_boost", false);
    public static final PotionEffectType LEVITATION = new PotionEffectType("levitation", false);
    public static final PotionEffectType SLOW_FALLING = new PotionEffectType("slow_falling", false);
    public static final PotionEffectType HASTE = new PotionEffectType("haste", false);
    public static final PotionEffectType RESISTANCE = new PotionEffectType("resistance", false);
    public static final PotionEffectType REGEN = new PotionEffectType("regeneration", false);
    public static final PotionEffectType NIGHT_VISION = new PotionEffectType("night_vision", false);
    public static final PotionEffectType INVISIBILITY = new PotionEffectType("invisibility", false);
}
