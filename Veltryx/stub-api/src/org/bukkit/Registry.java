package org.bukkit;
public interface Registry<T extends Keyed> {
    Registry<org.bukkit.potion.PotionEffectType> POTION_EFFECT_TYPE = key -> null;
    Registry<Sound> SOUND_EVENT = null;
    T get(NamespacedKey key);
}
