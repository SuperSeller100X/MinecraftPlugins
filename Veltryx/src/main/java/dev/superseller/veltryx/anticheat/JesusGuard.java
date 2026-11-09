package dev.superseller.veltryx.anticheat;

/**
 * Pure jesus-check math (no Bukkit) — unit-tested in the smoke suite.
 *
 * <p>Jesus = skating across the water surface at walking speed. Only true
 * surface skating counts: diving into water, walking on the sea floor,
 * swimming and depth-strider/dolphin movement all have the head under water
 * or solid ground underfoot and must never flag. The verdict also needs
 * several consecutive skimming samples, so single correction artifacts and
 * wave bounces say nothing.
 */
public final class JesusGuard {

    /** Consecutive skimming samples required before anything is flagged. */
    public static final int FLAG_STREAK = 3;

    private JesusGuard() {
    }

    /**
     * Is this move "skimming the surface"? (All inputs are server-side state.)
     *
     * @param headInWater    the block at eye height is water (diver/underwater walker)
     * @param solidBelow     solid ground directly under the feet (sea-floor walker)
     * @param dy             vertical delta of this move (blocks)
     * @param fallDistance   current fall distance (diving in from above)
     */
    public static boolean skimming(boolean headInWater, boolean solidBelow,
            double dy, float fallDistance) {
        return !headInWater && !solidBelow && Math.abs(dy) <= 0.08 && fallDistance <= 0.2F;
    }

    /** Whether a skimming streak has earned a flag. */
    public static boolean shouldFlag(int streak) {
        return streak >= FLAG_STREAK;
    }
}
