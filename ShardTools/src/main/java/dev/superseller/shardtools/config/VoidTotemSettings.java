package dev.superseller.shardtools.config;

import java.util.Locale;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed snapshot of the "void-totem:" section of config.yml. Controls the
 * rescue the Void Totem performs when its holder is about to die in the
 * void: where to pull them back, which buffs to grant, and which flavour
 * (animation/sound/particles) to play.
 */
public final class VoidTotemSettings {

    /** Where the rescued player is teleported to. */
    public enum RescueMode {
        /** The last grounded spot the player stood on (world spawn as fallback). */
        LAST_SAFE,
        /** The spawn point of the world the player fell in. */
        SPAWN,
        /** A fixed configured coordinate. */
        COORDS;

        public static RescueMode parse(String text, RescueMode fallback) {
            if (text == null) {
                return fallback;
            }
            try {
                return valueOf(text.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }

    /** Which slot a totem is consumed from. */
    public enum ConsumeFrom {
        /** Any inventory slot. */
        INVENTORY,
        /** Main or off hand only. */
        HAND;

        public static ConsumeFrom parse(String text, ConsumeFrom fallback) {
            if (text == null) {
                return fallback;
            }
            try {
                return valueOf(text.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }

    private boolean enabled = true;
    private long cooldownSeconds = 5;
    private boolean clearHarmful = true;
    private long resistanceSeconds = 45;
    private long slowFallingSeconds = 30;
    private RescueMode rescueMode = RescueMode.LAST_SAFE;
    private String rescueWorld = "";
    private double rescueX = 0.5D;
    private double rescueY = 100.0D;
    private double rescueZ = 0.5D;
    private ConsumeFrom consumeFrom = ConsumeFrom.INVENTORY;
    private boolean animation = true;
    private boolean sound = true;
    private boolean particlesEnabled = true;
    private String particleId = "PORTAL";
    private int particleCount = 40;

    /** (Re-)reads the "void-totem:" section; call after /sta reload. */
    public void load(FileConfiguration config) {
        enabled = config.getBoolean("void-totem.enabled", true);
        cooldownSeconds = Math.max(1L, config.getLong("void-totem.cooldown-seconds", 5L));
        clearHarmful = config.getBoolean("void-totem.clear-harmful", true);
        resistanceSeconds = Math.max(0L, config.getLong("void-totem.resistance-seconds", 45L));
        slowFallingSeconds = Math.max(0L, config.getLong("void-totem.slow-falling-seconds", 30L));
        rescueMode = RescueMode.parse(config.getString("void-totem.rescue-mode"), RescueMode.LAST_SAFE);
        String world = config.getString("void-totem.rescue-world", "");
        rescueWorld = world == null ? "" : world.trim();
        rescueX = config.getDouble("void-totem.rescue-x", 0.5D);
        rescueY = config.getDouble("void-totem.rescue-y", 100.0D);
        rescueZ = config.getDouble("void-totem.rescue-z", 0.5D);
        consumeFrom = ConsumeFrom.parse(config.getString("void-totem.consume-from"), ConsumeFrom.INVENTORY);
        animation = config.getBoolean("void-totem.animation", true);
        sound = config.getBoolean("void-totem.sound", true);
        particlesEnabled = config.getBoolean("void-totem.particles.enabled", true);
        particleId = config.getString("void-totem.particles.id", "PORTAL");
        particleCount = Math.max(1, config.getInt("void-totem.particles.count", 40));
    }

    /** Master switch: false disables the void rescue entirely. */
    public boolean enabled() {
        return enabled;
    }

    /** Minimum seconds between two rescues (blocks double consumption). */
    public long cooldownSeconds() {
        return cooldownSeconds;
    }

    /** Remove poison/wither/slowness... after the rescue. */
    public boolean clearHarmful() {
        return clearHarmful;
    }

    /** Resistance duration after the rescue (0 = none). */
    public long resistanceSeconds() {
        return resistanceSeconds;
    }

    /** Slow Falling duration after the rescue (0 = none). */
    public long slowFallingSeconds() {
        return slowFallingSeconds;
    }

    public RescueMode rescueMode() {
        return rescueMode;
    }

    /** World name for {@link RescueMode#COORDS}; empty = the player's world. */
    public String rescueWorld() {
        return rescueWorld;
    }

    public double rescueX() {
        return rescueX;
    }

    public double rescueY() {
        return rescueY;
    }

    public double rescueZ() {
        return rescueZ;
    }

    public ConsumeFrom consumeFrom() {
        return consumeFrom;
    }

    /** Play the vanilla totem-of-undying animation. */
    public boolean animation() {
        return animation;
    }

    /** Play the vanilla totem-of-undying use sound. */
    public boolean sound() {
        return sound;
    }

    public boolean particlesEnabled() {
        return particlesEnabled;
    }

    public String particleId() {
        return particleId;
    }

    public int particleCount() {
        return particleCount;
    }
}
