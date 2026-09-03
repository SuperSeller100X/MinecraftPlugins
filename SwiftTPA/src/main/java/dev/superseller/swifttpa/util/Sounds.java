package dev.superseller.swifttpa.util;

import dev.superseller.swifttpa.config.SwiftTpaConfig;

import net.kyori.adventure.sound.Sound;

import org.bukkit.entity.Player;

/**
 * Plays configurable sound effects to a single player. Safe to call from the
 * player's owning thread. Silently does nothing when sounds are disabled or
 * the configured sound key is invalid.
 */
public final class Sounds {

    private Sounds() {
    }

    public static void play(SwiftTpaConfig config, Player player, String key) {
        play(config, player, key, null);
    }

    /**
     * Plays an event sound, optionally overriding the pitch (used by the
     * warmup countdown to rise towards zero).
     */
    public static void play(SwiftTpaConfig config, Player player, String key, Float pitchOverride) {
        if (player == null || !config.soundsEnabled()) {
            return;
        }
        Sound sound = config.sound(key, pitchOverride);
        if (sound == null) {
            return;
        }
        try {
            player.playSound(sound);
        } catch (Throwable ignored) {
            // A malformed sound must never break a teleport flow.
        }
    }
}
