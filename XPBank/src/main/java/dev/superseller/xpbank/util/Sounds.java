package dev.superseller.xpbank.util;

import dev.superseller.xpbank.config.XPBankConfig;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Plays configurable sound effects to a single player. Safe to call from the
 * player's owning thread. Silently does nothing when sounds are disabled or the
 * configured sound name is invalid.
 */
public final class Sounds {

    private Sounds() {
    }

    public static void play(XPBankConfig config, Player player, String key) {
        if (player == null || !config.soundsEnabled()) {
            return;
        }
        Sound sound = config.sound(key);
        if (sound == null) {
            return;
        }
        try {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (Throwable ignored) {
            // A malformed sound must never break a transaction.
        }
    }
}
