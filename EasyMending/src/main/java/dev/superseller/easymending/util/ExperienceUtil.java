package dev.superseller.easymending.util;

import org.bukkit.entity.Player;

/**
 * High-precision vanilla Minecraft experience manager.
 * Interacts with Bukkit Player entities, synchronizing levels and exp bar progress.
 */
public final class ExperienceUtil {

    private ExperienceUtil() {
    }

    public static int getPointsForLevel(int level) {
        return ExperienceCalculator.getPointsForLevel(level);
    }

    public static int getTotalPointsAtLevel(int level) {
        return ExperienceCalculator.getTotalPointsAtLevel(level);
    }

    public static int getLevelForPoints(int totalPoints) {
        return ExperienceCalculator.getLevelForPoints(totalPoints);
    }

    /**
     * Calculates a player's exact total experience points, including partial level progress.
     *
     * @param player player to inspect
     * @return exact total experience points
     */
    public static int getPlayerTotalExperience(Player player) {
        if (player == null) {
            return 0;
        }
        int level = player.getLevel();
        float exp = Math.max(0.0f, Math.min(1.0f, player.getExp()));
        int basePoints = getTotalPointsAtLevel(level);
        int pointsToNext = getPointsForLevel(level);
        int progressPoints = Math.round(pointsToNext * exp);
        return Math.max(0, basePoints + progressPoints);
    }

    /**
     * Sets a player's experience points accurately, synchronizing their level, exp progress bar,
     * and total experience tracker.
     *
     * @param player player to update
     * @param totalPoints total experience points to set (non-negative)
     */
    public static void setPlayerExperience(Player player, int totalPoints) {
        if (player == null) {
            return;
        }
        int target = Math.max(0, totalPoints);
        int level = getLevelForPoints(target);
        int baseAtLevel = getTotalPointsAtLevel(level);
        int remaining = target - baseAtLevel;
        int neededForNext = getPointsForLevel(level);

        float fraction = neededForNext > 0 ? (float) remaining / (float) neededForNext : 0.0f;
        fraction = Math.max(0.0f, Math.min(0.9999f, fraction));

        player.setTotalExperience(target);
        player.setLevel(level);
        player.setExp(fraction);
    }

    /**
     * Deducts a specific amount of experience points from a player.
     *
     * @param player player to deduct from
     * @param points number of points to deduct
     * @return true if player had enough points and deduction succeeded; false otherwise
     */
    public static boolean deductExperience(Player player, int points) {
        if (player == null || points < 0) {
            return false;
        }
        if (points == 0) {
            return true;
        }
        int current = getPlayerTotalExperience(player);
        if (current < points) {
            return false;
        }
        setPlayerExperience(player, current - points);
        return true;
    }

    /**
     * Awards experience points to a player.
     *
     * @param player player to credit
     * @param points points to add
     */
    public static void addExperience(Player player, int points) {
        if (player == null || points <= 0) {
            return;
        }
        int current = getPlayerTotalExperience(player);
        setPlayerExperience(player, current + points);
    }
}
