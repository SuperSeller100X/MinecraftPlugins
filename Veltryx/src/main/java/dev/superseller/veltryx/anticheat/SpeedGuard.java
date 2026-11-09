package dev.superseller.veltryx.anticheat;

/**
 * Pure speed-check math (no Bukkit) — unit-tested in the smoke suite.
 *
 * <p>The tolerance accounts for sprint-jump chains, potion effects and
 * supporting terrain rather than just flat sprinting. These limits are
 * heuristics: server mechanics and plugins can still produce unexpected moves,
 * so the hard cap is not proof that a client is cheating.
 */
public final class SpeedGuard {

    /** Within tolerance — never flagged. */
    public static final int OK = 0;
    /** Mild excess — counted toward the streak, never flagged alone. */
    public static final int SUSPECT = 1;
    /** Sustained excess (streak) — flag and escalate. */
    public static final int FLAG = 2;
    /** Hard-cap excess — eligible for a sustained server-authority clamp. */
    public static final int ROLLBACK = 3;

    /** Excess below this ratio is treated as ordinary jitter. */
    public static final double SUSPECT_RATIO = 1.15;
    /** Consecutive excess samples required before a flag is issued. */
    public static final int FLAG_STREAK = 3;

    private SpeedGuard() {
    }

    /**
     * Situational multiplier from potion effects and terrain (never below 1.0).
     * All inputs describe real server-side state — a client cannot fake them.
     *
     * @param speedAmplifier amplifier of the SPEED effect, or -1 when absent
     * @param ice            standing on ice / packed / blue / frosted ice
     * @param slime          standing on slime or honey blocks (launch pads)
     * @param soulTerrain    standing on soul sand or soul soil (Soul Speed)
     * @param jumpChain      inside a sprint-jump / jump chain (fastest legit movement)
     */
    public static double situationalMultiplier(int speedAmplifier, boolean ice,
            boolean slime, boolean soulTerrain, boolean jumpChain) {
        double multiplier = 1.0;
        if (speedAmplifier >= 0) {
            multiplier *= 1.0 + 0.2 * (speedAmplifier + 1);
        }
        if (ice) {
            multiplier *= 1.6;
        }
        if (slime) {
            multiplier *= 1.5;
        }
        if (soulTerrain) {
            multiplier *= 1.45;
        }
        if (jumpChain) {
            multiplier *= 1.42;
        }
        return Math.min(4.5, multiplier);
    }

    /** Full speed limit in blocks/s for one sample. */
    public static double limit(double maxMoveSpeed, double lagFactor, double multiplier) {
        return maxMoveSpeed * lagFactor * multiplier;
    }

    /**
     * Smooth server-authority clamp: scale a horizontal delta so it covers at
     * most {@code maxDistance} while keeping the movement direction. The
     * player glides at the cap instead of being snapped back — no rubber-band
     * stutter, no lag feel, and speed hacks cannot outrun the clamp.
     *
     * @return {@code {clampedDx, clampedDz}}
     */
    public static double[] clampDelta(double dx, double dz, double maxDistance) {
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= maxDistance || distance == 0.0) {
            return new double[] {dx, dz};
        }
        double scale = maxDistance / distance;
        return new double[] {dx * scale, dz * scale};
    }

    /**
     * Verdict for one movement sample.
     *
     * @param horizontal        horizontal blocks/s of the sample
     * @param limit             the situational limit for this sample
     * @param hardCapMultiplier limit × this is impossible for vanilla clients
     * @param streak            consecutive excess samples before this one
     */
    public static int verdict(double horizontal, double limit, double hardCapMultiplier, int streak) {
        if (horizontal <= limit * SUSPECT_RATIO) {
            return OK;
        }
        if (horizontal > limit * hardCapMultiplier) {
            return ROLLBACK;
        }
        return streak + 1 >= FLAG_STREAK ? FLAG : SUSPECT;
    }
}
