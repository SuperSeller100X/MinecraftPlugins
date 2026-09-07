package dev.superseller.playerbank.util;

import dev.superseller.playerbank.config.BankConfig;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Plays configurable GUI sound effects to a single player. Silently does
 * nothing when sounds are disabled or the configured sound name is invalid —
 * a malformed sound must never break a transaction.
 */
public final class Sounds {

    private Sounds() {
    }

    public static void play(BankConfig config, Player player, String key) {
        if (player == null) {
            return;
        }
        Sound sound = config.sound(key);
        if (sound == null) {
            return;
        }
        try {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (Throwable ignored) {
            // Never let a sound failure break a bank action.
        }
    }
}
