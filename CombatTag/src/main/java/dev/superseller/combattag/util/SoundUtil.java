package dev.superseller.combattag.util;

import dev.superseller.combattag.config.PluginConfig;
import org.bukkit.entity.Player;

/** Plays configured sound effects safely (never throws into game logic). */
public final class SoundUtil {

    private SoundUtil() {
    }

    public static void play(Player player, PluginConfig.SoundEffect effect, boolean enabled) {
        if (!enabled || player == null || !player.isOnline() || effect == null || effect.sound() == null) {
            return;
        }
        try {
            player.playSound(player.getLocation(), effect.sound(), effect.volume(), effect.pitch());
        } catch (Throwable ignored) {
            // Never let a bad sound key break gameplay
        }
    }
}
