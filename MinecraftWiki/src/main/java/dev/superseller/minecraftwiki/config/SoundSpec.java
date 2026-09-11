package dev.superseller.minecraftwiki.config;

import org.bukkit.entity.Player;

import dev.superseller.minecraftwiki.util.Sounds;

/**
 * A configurable sound: namespaced key plus volume and pitch.
 *
 * @param key    sound id, blank means silent
 * @param volume 0 disables playback
 * @param pitch  0.5 to 2 is the vanilla range, other values are accepted by the client
 */
public record SoundSpec(String key, float volume, float pitch) {

    public static final SoundSpec SILENT = new SoundSpec("", 0f, 1f);

    public SoundSpec {
        key = key == null ? "" : key;
        volume = Math.max(0f, volume);
        pitch = Math.max(0f, pitch);
    }

    public boolean silent() {
        return key.isBlank() || volume <= 0f;
    }

    public void play(Player player) {
        if (!silent()) {
            Sounds.play(player, key, volume, pitch);
        }
    }
}
