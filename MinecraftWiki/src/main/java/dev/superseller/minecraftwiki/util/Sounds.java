package dev.superseller.minecraftwiki.util;

import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Configurable sound playback.
 *
 * <p>Sounds are played by namespaced key string so that any vanilla, datapack or
 * resource-pack sound works and an unknown name can never throw. Names are validated
 * once against {@link Registry#SOUNDS} when configuration loads so typos are reported
 * instead of silently doing nothing.</p>
 */
public final class Sounds {

    private Sounds() {
    }

    /** Plays a sound for one player. A null/blank key or a negative volume is a no-op. */
    public static void play(Player player, String key, float volume, float pitch) {
        if (player == null || key == null || key.isBlank() || volume < 0f) {
            return;
        }
        try {
            player.playSound(player.getLocation(), key, Math.max(0f, volume), pitch);
        } catch (Throwable ignored) {
            // A server that rejects an unknown sound id must never break the GUI.
        }
    }

    /**
     * Checks a configured sound name against the sound registry.
     *
     * @return {@code null} when the name is known or blank, otherwise a human readable warning
     */
    public static String validate(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            NamespacedKey namespaced = key.indexOf(':') >= 0
                    ? NamespacedKey.fromString(key)
                    : NamespacedKey.minecraft(key);
            if (namespaced == null) {
                return "sound '" + key + "' is not a valid namespaced key";
            }
            Sound sound = Registry.SOUNDS.get(namespaced);
            if (sound == null) {
                return "sound '" + key + "' does not exist in the sound registry";
            }
        } catch (Throwable error) {
            return "sound '" + key + "' could not be validated (" + error.getClass().getSimpleName() + ")";
        }
        return null;
    }

    /** Reports every invalid configured sound name once at startup. */
    public static void validateAll(Set<String> keys, Logger logger, String source) {
        if (keys == null) {
            return;
        }
        for (String key : keys) {
            String problem = validate(key);
            if (problem != null) {
                logger.warning("[Wiki/Config] " + source + ": " + problem + " (it will be silent)");
            }
        }
    }
}
