package dev.superseller.justgambling.util;

import dev.superseller.justgambling.config.PluginSettings;

import java.util.Locale;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Configurable registry-backed sound effects for the 26.2 API. */
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
        Sound sound = resolve(configured);
        if (sound == null && !configured.equalsIgnoreCase(fallback)) {
            sound = resolve(fallback);
        }
        if (sound != null) {
            player.playSound(player.getLocation(), sound, settings.soundVolume(), settings.soundPitch());
        }
    }

    private Sound resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = value.indexOf(':') >= 0
                ? NamespacedKey.fromString(value)
                : NamespacedKey.minecraft(value);
        Sound sound = key == null ? null : Registry.SOUND_EVENT.get(key);
        if (sound != null) {
            return sound;
        }
        // Bukkit-style constants such as UI_BUTTON_CLICK map to the
        // namespaced sound key ui.button.click in the 26.2 registry.
        String dotted = value.replace('_', '.');
        NamespacedKey dottedKey = dotted.indexOf(':') >= 0
                ? NamespacedKey.fromString(dotted)
                : NamespacedKey.minecraft(dotted);
        return dottedKey == null ? null : Registry.SOUND_EVENT.get(dottedKey);
    }
}
