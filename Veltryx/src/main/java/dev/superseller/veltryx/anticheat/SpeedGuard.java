package dev.superseller.veltryx.anticheat;

/**
 * Pure speed-check math (no Bukkit) — unit-tested in the smoke suite.
 *
 * <p>The anti-cheat promise is "no false flags", so the tolerance here is
 * built around the fastest legitimate vanilla movement, which is the
 * <b>sprint-jump chain</b> (~7.5–8 blocks/s on flat ground, ~11 with Speed II),
 * not flat sprinting (~5.6). Anything the maths cannot explain gets flagged;
 * anything above the hard cap is impossible for a vanilla client and is rolled
 * back by server authority.
 */
public final class SpeedGuard {

    /** Within tolerance — never flagged. */
    public static final int OK = 0;
    /** Mild excess — counted toward the streak, never flagged alone. */
    public static final int SUSPECT = 1;
    /** Sustained excess (streak) — flag and escalate. */
    public static final int FLAG = 2;
    /** Impossible movement — server authority rolls it back and flags. */
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
