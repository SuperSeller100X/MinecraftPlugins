package dev.superseller.justgambling.util;

import dev.superseller.justgambling.config.PluginSettings;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Configurable sound effects with safe fallback for renamed/invalid sounds. */
public final class Sounds {
    private final PluginSettings settings;

    public Sounds(PluginSettings settings) {
        this.settings = settings;
    }

    public void play(Player player, String key, String fallback) {
        if (player == null || !player.isOnline()) {
            return;
        }
        String configured = settings.sound(key, fallback);
        if (configured.isBlank() || configured.equalsIgnoreCase("none") || configured.equalsIgnoreCase("off")) {
            return;
        }
        try {
            Sound sound = Sound.valueOf(configured.toUpperCase(java.util.Locale.ROOT));
            player.playSound(player.getLocation(), sound, settings.soundVolume(), settings.soundPitch());
        } catch (IllegalArgumentException ignored) {
            if (!configured.equalsIgnoreCase(fallback)) {
                try {
                    player.playSound(player.getLocation(), Sound.valueOf(fallback), settings.soundVolume(), settings.soundPitch());
                } catch (IllegalArgumentException ignoredFallback) {
                    // A bad custom sound should never break a wager.
                }
            }
        }
    }
}
