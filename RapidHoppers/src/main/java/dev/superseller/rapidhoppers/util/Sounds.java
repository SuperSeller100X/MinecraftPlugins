package dev.superseller.rapidhoppers.util;

import java.util.Locale;

import dev.superseller.rapidhoppers.config.Settings;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Config-driven sound playback.
 *
 * <p>Sound names are resolved by name so servers can pick any sound available
 * on Minecraft 26.2 without a plugin update; unknown names are logged once and
 * then ignored instead of throwing.</p>
 */
public final class Sounds {

    public static final String GUI_OPEN = "gui-open";
    public static final String GUI_CLICK = "gui-click";
    public static final String GUI_CLOSE = "gui-close";
    public static final String SUCCESS = "success";
    public static final String ERROR = "error";
    public static final String TOGGLE_ON = "toggle-on";
    public static final String TOGGLE_OFF = "toggle-off";

    private final JavaPlugin plugin;
    private final Settings settings;

    public Sounds(JavaPlugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    /** Plays a configured sound key at the player's location. */
    public void play(Player player, String key) {
        if (player == null || !settings.isSoundsEnabled()) {
            return;
        }
        String name = plugin.getConfig().getString("sounds." + key + ".sound", "");
        if (name == null || name.isBlank() || "NONE".equalsIgnoreCase(name)) {
            return;
        }
        float volume = (float) plugin.getConfig().getDouble("sounds." + key + ".volume", 1.0D);
        float pitch = (float) plugin.getConfig().getDouble("sounds." + key + ".pitch", 1.0D);
        Sound sound = resolve(name);
        if (sound == null) {
            return;
        }
        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Throwable ignored) {
            // never let a cosmetic sound break a command
        }
    }

    /** Plays either the on or off toggle sound. */
    public void playToggle(Player player, boolean on) {
        play(player, on ? TOGGLE_ON : TOGGLE_OFF);
    }

    private Sound resolve(String name) {
        String normalised = name.trim().toUpperCase(Locale.ROOT).replace('.', '_').replace(':', '_');
        // Sound is an interface backed by a registry on modern Paper, but the
        // generated constants are still reachable through valueOf-style
        // reflection, which keeps this jar free of version-specific classes.
        try {
            Object value = Sound.class.getMethod("valueOf", String.class).invoke(null, normalised);
            if (value instanceof Sound s) {
                return s;
            }
        } catch (Throwable ignored) {
            // Sound is registry-backed on this server - try the registry next
        }
        try {
            Class<?> keyClass = Class.forName("org.bukkit.NamespacedKey");
            Object key = keyClass.getMethod("minecraft", String.class)
                    .invoke(null, normalised.toLowerCase(Locale.ROOT).replace('_', '.'));
            Object registry = Class.forName("org.bukkit.Registry").getField("SOUNDS").get(null);
            Object value = registry.getClass().getMethod("get", keyClass).invoke(registry, key);
            if (value instanceof Sound s) {
                return s;
            }
        } catch (Throwable ignored) {
            // genuinely unknown sound - reported below
        }
        if (settings.isDebug()) {
            plugin.getLogger().warning("Unknown sound in config: " + name);
        }
        return null;
    }
}
