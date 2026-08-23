package dev.superseller.chestlock.config;

import java.util.Locale;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

/** Immutable, validated runtime view of config.yml. */
public record PluginConfig(
        int targetDistance,
        boolean lockChests,
        boolean lockBarrels,
        boolean lockShulkerBoxes,
        int minPasscodeLength,
        int maxPasscodeLength,
        int hashIterations,
        int attemptsBeforeCooldown,
        long attemptWindowMillis,
        long baseCooldownMillis,
        long maxCooldownMillis,
        int minDurationSeconds,
        int maxDurationSeconds,
        int defaultDurationSeconds,
        boolean protectExplosions,
        boolean protectPistons,
        boolean protectFire,
        boolean protectEntityChanges,
        boolean blockHoppers,
        Material keyMaterial,
        boolean keyGlint,
        boolean defaultCreateKey,
        boolean defaultAutoOpen,
        boolean defaultSounds,
        boolean defaultParticles,
        boolean defaultActionBar,
        boolean defaultBreakConfirmation,
        boolean defaultAttemptNotifications,
        Sound successSound,
        Sound failureSound,
        Sound lockedSound,
        Particle successParticle,
        boolean clearSessionsOnQuit,
        long dialogTimeoutMillis,
        long breakConfirmationMillis,
        boolean auditActions
) {
    public static PluginConfig load(FileConfiguration config, Consumer<String> warning) {
        int minPasscode = bounded(config.getInt("passcodes.min-length", 4), 1, 128);
        int maxPasscode = bounded(config.getInt("passcodes.max-length", 64), minPasscode, 256);
        int minDuration = bounded(config.getInt("unlock-duration.min-seconds", 5), 1, 86_400);
        int maxDuration = bounded(config.getInt("unlock-duration.max-seconds", 3_600), minDuration, 86_400);

        return new PluginConfig(
                bounded(config.getInt("target-distance", 6), 1, 32),
                config.getBoolean("containers.chests", true),
                config.getBoolean("containers.barrels", true),
                config.getBoolean("containers.shulker-boxes", true),
                minPasscode,
                maxPasscode,
                bounded(config.getInt("passcodes.iterations", 210_000), 10_000, 2_000_000),
                bounded(config.getInt("passcodes.attempts-before-cooldown", 3), 1, 100),
                secondsToMillis(bounded(config.getLong("passcodes.attempt-window-seconds", 30), 1, 3_600)),
                secondsToMillis(bounded(config.getLong("passcodes.base-cooldown-seconds", 5), 1, 3_600)),
                secondsToMillis(bounded(config.getLong("passcodes.max-cooldown-seconds", 300), 1, 86_400)),
                minDuration,
                maxDuration,
                bounded(config.getInt("unlock-duration.default-seconds", 30), minDuration, maxDuration),
                config.getBoolean("protection.explosions", true),
                config.getBoolean("protection.pistons", true),
                config.getBoolean("protection.fire", true),
                config.getBoolean("protection.entity-block-changes", true),
                config.getBoolean("protection.block-hoppers", false),
                enumValue(Material.class, config.getString("keys.material", "TRIPWIRE_HOOK"), Material.TRIPWIRE_HOOK, warning, "keys.material"),
                config.getBoolean("keys.glint", true),
                config.getBoolean("player-defaults.create-key", false),
                config.getBoolean("player-defaults.auto-open", false),
                config.getBoolean("player-defaults.sounds", true),
                config.getBoolean("player-defaults.particles", true),
                config.getBoolean("player-defaults.action-bar", true),
                config.getBoolean("player-defaults.break-confirmation", true),
                config.getBoolean("player-defaults.attempt-notifications", true),
                soundValue(config.getString("feedback.success-sound", "BLOCK_NOTE_BLOCK_PLING"), Sound.BLOCK_NOTE_BLOCK_PLING, warning, "feedback.success-sound"),
                soundValue(config.getString("feedback.failure-sound", "ENTITY_VILLAGER_NO"), Sound.ENTITY_VILLAGER_NO, warning, "feedback.failure-sound"),
                soundValue(config.getString("feedback.locked-sound", "BLOCK_CHEST_LOCKED"), Sound.BLOCK_CHEST_LOCKED, warning, "feedback.locked-sound"),
                enumValue(Particle.class, config.getString("feedback.success-particle", "HAPPY_VILLAGER"), Particle.HAPPY_VILLAGER, warning, "feedback.success-particle"),
                config.getBoolean("security.clear-sessions-on-quit", true),
                secondsToMillis(bounded(config.getLong("security.dialog-timeout-seconds", 120), 10, 600)),
                secondsToMillis(bounded(config.getLong("security.break-confirmation-seconds", 4), 1, 30)),
                config.getBoolean("logging.audit-actions", true)
        );
    }

    private static int bounded(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long bounded(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long secondsToMillis(long seconds) {
        return Math.multiplyExact(seconds, 1_000L);
    }

    @SuppressWarnings("deprecation")
    private static Sound soundValue(String configured, Sound fallback, Consumer<String> warning, String path) {
        try {
            return Sound.valueOf(configured.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            warning.accept("Invalid " + path + " value '" + configured + "'; using the default.");
            return fallback;
        }
    }

    private static <E extends Enum<E>> E enumValue(
            Class<E> type,
            String configured,
            E fallback,
            Consumer<String> warning,
            String path
    ) {
        try {
            return Enum.valueOf(type, configured.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            warning.accept("Invalid " + path + " value '" + configured + "'; using " + fallback.name() + '.');
            return fallback;
        }
    }
}
