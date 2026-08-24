package dev.superseller.shardtools.item;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import dev.superseller.shardtools.ShardToolsPlugin;

/**
 * DonutSMP sound & particle flavour. ONE amethyst step sound plays per USE
 * of a shard tool (a 3x3 break = one sound, not nine); equipping a shard
 * item plays the amethyst resonate sound. Purple portal particles burst per
 * broken block. Everything is configurable under "effects:" (sound lists =
 * random pick per use).
 */
public final class Effects {

    private final ShardToolsPlugin plugin;
    private final Logger logger;

    public Effects(ShardToolsPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * ONE amethyst sound per USE of the tool (a 3x3 break plays it once,
     * not once per block) - the amethyst step sound, or a random pick from
     * the configured list.
     */
    public void mineUse(Player player) {
        if (plugin.settings().mineSoundEnabled()) {
            List<SoundSpec> sounds = plugin.settings().mineSounds();
            if (!sounds.isEmpty()) {
                SoundSpec spec = sounds.get(
                        ThreadLocalRandom.current().nextInt(sounds.size()));
                play(player, spec);
            }
        }
    }

    /** Purple portal particle burst at a broken block. */
    public void mineBlockParticles(Location blockLocation) {
        if (plugin.settings().mineParticlesEnabled() && blockLocation != null) {
            burst(blockLocation.getWorld(), blockLocation,
                    plugin.settings().mineParticleId(), plugin.settings().mineParticleCount());
        }
    }

    /** Netherite armor equip sound + purple particle ring when equipped. */
    public void equipEffect(Player player) {
        equipSound(player);
        equipRing(player);
    }

    /** Only the equip sound (used where particles would double up). */
    public void equipSound(Player player) {
        SoundSpec spec = plugin.settings().equipSound();
        if (plugin.settings().equipSoundEnabled() && spec != null) {
            play(player, spec);
        }
    }

    /** Only the purple particle ring (used for purchases). */
    public void equipRing(Player player) {
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

    private void play(Player player, SoundSpec spec) {
        Sound sound = plugin.soundResolver().resolve(spec.id());
        if (sound != null) {
            player.playSound(player.getLocation(), sound, spec.volume(), spec.pitch());
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
