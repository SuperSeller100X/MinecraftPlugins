package dev.superseller.veltryx.anticheat;

/**
 * Pure fly-check math (no Bukkit) — unit-tested in the smoke suite.
 *
 * <p>Vertical movement is judged in three tiers:
 * <ul>
 *   <li><b>real fall</b> ({@code dy < -0.35}) — gravity accelerates past that
 *       in a fraction of a second, so anything faster is genuine falling and
 *       drains the air counter;</li>
 *   <li><b>slow descent</b> ({@code -0.35 <= dy < -0.08}) — vanilla clients
 *       cannot sustain this (falls accelerate; the only slow descents are
 *       elytra/riptide/potions, which are exempt elsewhere). A "no-fall" style
 *       fly that drifts down gently used to hide here — it now counts as
 *       airtime;</li>
 *   <li><b>hover / rise</b> ({@code dy >= -0.08}) — counts as airtime.</li>
 * </ul>
 * Only real falls may decay the counter, so bobbing cannot drain it either.
 */
public final class FlyGuard {

    /** Below this dy the movement is a genuine accelerating fall. */
    public static final double REAL_FALL_DY = -0.35;

    private FlyGuard() {
    }

    /**
     * Next air-tick counter value.
     *
     * @param airTicks     current counter (move samples of unexplained airtime)
     * @param dy           vertical delta of this move (blocks)
     * @param onGround     server-computed ground contact
     * @param levitation   player has the levitation effect
     * @param slowFalling  player has the slow falling effect
     */
    public static int step(int airTicks, double dy, boolean onGround,
            boolean levitation, boolean slowFalling) {
        if (onGround || levitation || slowFalling) {
            return 0;
        }
        if (dy < REAL_FALL_DY) {
            return Math.max(0, airTicks - 4);
        }
        return airTicks + 1;
    }

    /**
     * Airtime (seconds) a player may accumulate before flight is flagged.
     * Scaled by jump-boost level and the ping/lag factor.
     */
    public static double maxAirSeconds(int jumpBoostAmplifier, double lagFactor) {
        return (0.8 + 0.12 * Math.max(0, jumpBoostAmplifier)) * lagFactor;
    }
}
