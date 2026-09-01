package dev.superseller.easymending.util;

import dev.superseller.easymending.config.PluginConfig;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Sound and visual feedback helper for player interactions.
 */
public final class SoundUtil {

    private SoundUtil() {
    }

    public static void play(Player player, Sound sound, float volume, float pitch, boolean enabled) {
        if (!enabled || player == null || !player.isOnline() || sound == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    public static void playRepairSuccess(Player player, PluginConfig config) {
        play(player, config.getSoundRepairSuccess(), config.getVolRepairSuccess(), config.getPitchRepairSuccess(), config.isSoundsEnabled());
        spawnParticles(player, config);
    }

    public static void playRepairAll(Player player, PluginConfig config) {
        play(player, config.getSoundRepairAll(), config.getVolRepairAll(), config.getPitchRepairAll(), config.isSoundsEnabled());
        spawnParticles(player, config);
    }

    public static void playNoDamage(Player player, PluginConfig config) {
        play(player, config.getSoundNoDamage(), config.getVolNoDamage(), config.getPitchNoDamage(), config.isSoundsEnabled());
    }

    public static void playNoMending(Player player, PluginConfig config) {
        play(player, config.getSoundNoMending(), config.getVolNoMending(), config.getPitchNoMending(), config.isSoundsEnabled());
    }

    public static void playInsufficientXp(Player player, PluginConfig config) {
        play(player, config.getSoundInsufficientXp(), config.getVolInsufficientXp(), config.getPitchInsufficientXp(), config.isSoundsEnabled());
    }

    public static void playGuiClick(Player player, PluginConfig config) {
        play(player, config.getSoundGuiClick(), config.getVolGuiClick(), config.getPitchGuiClick(), config.isSoundsEnabled());
    }

    public static void playGuiOpen(Player player, PluginConfig config) {
        play(player, config.getSoundGuiOpen(), config.getVolGuiOpen(), config.getPitchGuiOpen(), config.isSoundsEnabled());
    }

    private static void spawnParticles(Player player, PluginConfig config) {
        if (!config.isParticlesEnabled() || player == null || !player.isOnline()) {
            return;
        }
        try {
            player.getWorld().spawnParticle(
                    config.getParticleType(),
                    player.getLocation().add(0, 1.0, 0),
                    config.getParticleCount(),
                    0.4, 0.4, 0.4, 0.05
            );
        } catch (Throwable ignored) {
            // Safe fallback if particle type is unspawnable in current context
        }
    }
}
