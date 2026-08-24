package dev.superseller.shardtools.item;

import java.lang.reflect.Field;
import java.util.logging.Logger;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

/**
 * Resolves config sound ids ("entity.player.levelup") to Sound instances via
 * the registry, with a reflection fallback. Missing sounds are skipped
 * silently so bad config values never break gameplay.
 */
public final class SoundResolver {

    private final Logger logger;

    public SoundResolver(Logger logger) {
        this.logger = logger;
    }

    public Sound resolve(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        String key = id.replace("minecraft:", "").toLowerCase(java.util.Locale.ROOT);
        try {
            Registry<Sound> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT);
            Sound sound = registry.get(NamespacedKey.minecraft(key));
            if (sound != null) {
                return sound;
            }
        } catch (Throwable ignored) {
            // fall through to constant lookup
        }
        try {
            Field field = Sound.class.getField(key.toUpperCase(java.util.Locale.ROOT).replace('.', '_'));
            return (Sound) field.get(null);
        } catch (Throwable ignored) {
            logger.warning("Unknown sound '" + id + "' (skipped)");
            return null;
        }
    }
}
