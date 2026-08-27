package dev.superseller.xpbank.util;

import org.bukkit.entity.Player;

/**
 * Reliable total-experience math.
 *
 * <p>Bukkit's {@link Player#getTotalExperience()} is famously unreliable: it is
 * not kept in sync when the client picks up orbs or when levels change, so it
 * cannot be trusted to read or set a player's real XP. Instead we compute the
 * exact total number of experience points a player holds from their current
 * level plus the progress bar, using the vanilla Minecraft formulas, and we set
 * XP by resetting to zero and granting the desired amount.
 *
 * <p>Formulas (Minecraft 1.8+, unchanged through 26.2):
 * <ul>
 *   <li>XP needed to advance from {@code level} to {@code level + 1}:
 *       {@code 2*level + 7} for 0-15, {@code 5*level - 38} for 16-30,
 *       {@code 9*level - 158} for 31+.</li>
 *   <li>Cumulative XP required to reach {@code level} from 0:
 *       {@code level^2 + 6*level} for 0-16,
 *       {@code 2.5*level^2 - 40.5*level + 360} for 17-31,
 *       {@code 4.5*level^2 - 162.5*level + 2220} for 32+.</li>
 * </ul>
 *
 * <p>This class is pure (no live server state) so it can be unit tested offline.
 */
public final class ExperienceUtil {

    private ExperienceUtil() {
    }

    /** XP points required to go from {@code level} to {@code level + 1}. */
    public static int xpToNext(int level) {
        if (level < 0) {
            return 7;
        }
        if (level <= 15) {
            return 2 * level + 7;
        }
        if (level <= 30) {
            return 5 * level - 38;
        }
        return 9 * level - 158;
    }

    /** Cumulative XP required to reach {@code level} starting from level 0. */
    public static int xpAtLevel(int level) {
        if (level <= 0) {
            return 0;
        }
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            return (int) Math.round(2.5 * level * level - 40.5 * level + 360.0);
        }
        return (int) Math.round(4.5 * level * level - 162.5 * level + 2220.0);
    }

    /**
     * Total XP points represented by a given whole level and progress bar
     * fraction ({@code exp} in the range 0.0 - 1.0).
     */
    public static int totalFromLevelAndExp(int level, float exp) {
        int base = xpAtLevel(level);
        int inLevel = Math.round(exp * xpToNext(level));
        return base + inLevel;
    }

    /**
     * The exact number of experience points a live player currently holds.
     * Works reliably across Paper, Purpur and Folia.
     */
    public static int getPlayerExp(Player player) {
        return totalFromLevelAndExp(player.getLevel(), player.getExp());
    }

    /**
     * Sets a player's total experience to exactly {@code amount} points by
     * clearing their XP and granting the requested amount. Must be called on the
     * player's owning thread (entity scheduler / main thread).
     */
    public static void setPlayerExp(Player player, int amount) {
        int safe = Math.max(0, amount);
        player.setExp(0f);
        player.setLevel(0);
        player.setTotalExperience(0);
        if (safe > 0) {
            player.giveExp(safe);
        }
    }

    /**
     * Adds {@code delta} points to a player's XP (delta may be negative). The
     * result is clamped at zero. Returns the player's new total.
     */
    public static int changePlayerExp(Player player, int delta) {
        int current = getPlayerExp(player);
        int updated = Math.max(0, current + delta);
        setPlayerExp(player, updated);
        return updated;
    }
}
