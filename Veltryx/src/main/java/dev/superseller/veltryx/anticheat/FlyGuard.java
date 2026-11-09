package dev.superseller.veltryx.anticheat;

/** Pure, elapsed-time-based fly-check math (no Bukkit). */
public final class FlyGuard {

    /** Below this 50 ms-equivalent delta the player is making a genuine fast fall. */
    public static final double REAL_FALL_DY = -0.35;
    /** A long packet/event gap is not continuous evidence of hovering. */
    public static final double MAX_SAMPLE_GAP_SECONDS = 0.25;

    private FlyGuard() {
    }

    /**
     * Updates unexplained airtime in seconds rather than assuming every event
     * is one tick. Real falls drain it; slow descents, rises and hovering add
     * elapsed time. Long gaps discard the accumulated evidence rather than
     * attributing missing packets to flight.
     */
    public static double step(double airSeconds, double dy, double elapsedSeconds,
            boolean supported, boolean levitation, boolean slowFalling) {
        if (supported || levitation || slowFalling || !Double.isFinite(elapsedSeconds)
                || elapsedSeconds < 0.0 || elapsedSeconds > MAX_SAMPLE_GAP_SECONDS) {
            return 0.0;
        }
        if (dy < REAL_FALL_DY) {
            return Math.max(0.0, airSeconds - 4.0 * elapsedSeconds);
        }
        return Math.max(0.0, airSeconds) + elapsedSeconds;
    }

    /**
     * Airtime allowed before flight is flagged. The potion amplifier is -1
     * when absent, 0 for Jump Boost I, and so on.
     */
    public static double maxAirSeconds(int jumpBoostAmplifier, double lagFactor) {
        return (0.8 + 0.12 * Math.max(0, jumpBoostAmplifier + 1)) * lagFactor;
    }
}
