package dev.superseller.homesgui.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import dev.superseller.homesgui.HomesGUIPlugin;
import dev.superseller.homesgui.config.HomesSettings;

/** Configurable, defensive sound playback. */
public final class SoundPlayer {

    private SoundPlayer() {
    }

    public static void play(HomesGUIPlugin plugin, Player player, String key) {
        HomesSettings.SoundSettings settings = plugin.settings().sounds();
        if (!settings.enabled() || player == null) {
            return;
        }
        Sound sound = settings.sounds().get(key);
        if (sound == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, settings.volume(), settings.pitch());
    }
}
