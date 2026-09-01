package dev.superseller.easymending.util;

/**
 * Pure mathematical formulas for Minecraft experience curves and durability calculations.
 * Completely decoupled from Bukkit APIs for standalone testability and offline execution.
 */
public final class ExperienceCalculator {

    private ExperienceCalculator() {
    }

    /**
     * Calculates the raw XP points required to advance from the given level to (level + 1).
     *
     * @param level current player level
     * @return XP points needed to level up
     */
    public static int getPointsForLevel(int level) {
        if (level < 0) {
            return 0;
        }
        if (level <= 15) {
            return 2 * level + 7;
        }
        if (level <= 30) {
            return 5 * level - 38;
        }
        return 9 * level - 158;
    }

    /**
     * Calculates the total cumulative XP points required to reach the given level from level 0.
     *
     * @param level target level
     * @return cumulative XP points
     */
    public static int getTotalPointsAtLevel(int level) {
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
     * Determines the level corresponding to a cumulative experience point sum.
     *
     * @param totalPoints total experience points
     * @return highest level achieved with the given points
     */
    public static int getLevelForPoints(int totalPoints) {
        if (totalPoints <= 0) {
            return 0;
        }
        int low = 0;
        int high = 1000;
        while (getTotalPointsAtLevel(high) <= totalPoints) {
            high *= 2;
        }
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int points = getTotalPointsAtLevel(mid);
            if (points <= totalPoints) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return Math.max(0, high);
    }

    /**
     * Calculates the XP cost required to restore the given missing durability.
     *
     * @param damage missing durability
     * @param durabilityPerXp durability restored per 1 XP point
     * @param multiplier cost multiplier (e.g. 1.0 for Mending, 1.5 for non-mending)
     * @param minXp minimum XP per repair
     * @return required XP points
     */
    public static int calculateRepairCost(int damage, double durabilityPerXp, double multiplier, int minXp) {
        if (damage <= 0) {
            return 0;
        }
        double ratio = Math.max(0.1, durabilityPerXp);
        double mult = Math.max(0.1, multiplier);
        int cost = (int) Math.ceil((damage / ratio) * mult);
        return Math.max(cost, Math.max(1, minXp));
    }

    /**
     * Calculates the maximum durability points that can be restored with the available XP budget.
     *
     * @param availableXp available XP points
     * @param durabilityPerXp durability restored per 1 XP point
     * @param multiplier cost multiplier
     * @return restorable durability points
     */
    public static int calculateAffordableDurability(int availableXp, double durabilityPerXp, double multiplier) {
        if (availableXp <= 0) {
            return 0;
        }
        double ratio = Math.max(0.1, durabilityPerXp);
        double mult = Math.max(0.1, multiplier);
        return (int) Math.floor((availableXp * ratio) / mult);
    }

    /**
     * Calculates the actual XP spent for a given amount of restored durability.
     *
     * @param restoredDurability restored durability points
     * @param durabilityPerXp durability restored per 1 XP point
     * @param multiplier cost multiplier
     * @return actual XP points spent
     */
    public static int calculateSpentXp(int restoredDurability, double durabilityPerXp, double multiplier) {
        if (restoredDurability <= 0) {
            return 0;
        }
        double ratio = Math.max(0.1, durabilityPerXp);
        double mult = Math.max(0.1, multiplier);
        return (int) Math.ceil((restoredDurability / ratio) * mult);
    }
}
