package org.bukkit;
public interface Registry<T extends Keyed> {
    Registry<org.bukkit.potion.PotionEffectType> POTION_EFFECT_TYPE = null;
    Registry<Sound> SOUND_EVENT = null;
    Registry<Particle> PARTICLE_TYPE = null;
    T get(NamespacedKey key);
}
