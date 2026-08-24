package dev.superseller.shardtools.item;

import java.util.Locale;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import dev.superseller.shardtools.ShardToolsPlugin;

/**
 * DonutSMP amethyst flavour: purple portal particle bursts and amethyst
 * chime sounds when shard items mine, get equipped or get purchased.
 * Every sound, particle and toggle is configurable in config.yml.
 */
public final class Effects {

    private final ShardToolsPlugin plugin;
    private final Logger logger;

    public Effects(ShardToolsPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /** Amethyst chime + purple particle burst for each block a shard tool broke. */
    public void mineEffect(Player player, Location blockLocation) {
        if (plugin.settings().mineSoundEnabled()) {
            play(player, plugin.settings().mineSoundId(),
                    plugin.settings().mineSoundVolume(), plugin.settings().mineSoundPitch());
        }
        if (plugin.settings().mineParticlesEnabled()) {
            burst(player.getWorld(), blockLocation, plugin.settings().mineParticleId(),
                    plugin.settings().mineParticleCount());
        }
    }

    /** Amethyst chime + particle ring when shard armor is equipped. */
    public void equipEffect(Player player) {
        if (plugin.settings().equipSoundEnabled()) {
            play(player, plugin.settings().equipSoundId(),
                    plugin.settings().equipSoundVolume(), plugin.settings().equipSoundPitch());
        }
        if (plugin.settings().equipParticlesEnabled()) {
            Location location = player.getLocation().add(0, 1, 0);
            World world = player.getWorld();
            Particle particle = resolveParticle(plugin.settings().equipParticleId());
            if (particle != null) {
                world.spawnParticle(particle, location,
                        plugin.settings().equipParticleCount(), 0.5, 0.5, 0.5, 0.02);
            }
        }
    }

    private void play(Player player, String soundId, float volume, float pitch) {
        Sound sound = plugin.soundResolver().resolve(soundId);
        if (sound != null) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private void burst(World world, Location location, String particleId, int count) {
        Particle particle = resolveParticle(particleId);
        if (particle != null && world != null && location != null) {
            world.spawnParticle(particle, location, Math.max(1, count), 0.25, 0.25, 0.25, 0.01);
        }
    }

    private Particle resolveParticle(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        String name = id.replace("minecraft:", "").replace('.', '_').toUpperCase(Locale.ROOT);
        try {
            // Works whether Particle is an enum or a registry-backed interface.
            return (Particle) Particle.class.getField(name).get(null);
        } catch (Throwable ignored) {
            logger.warning("Unknown particle '" + id + "' (skipped)");
            return null;
        }
    }
}
