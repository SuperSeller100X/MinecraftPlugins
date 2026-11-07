package dev.superseller.veltryx.anticheat;

/**
 * Statistical core of the X-Ray detection.
 *
 * <p>Ore mining is scored by how "hidden" each ore was: an ore buried in stone
 * is worth more suspicion than one exposed in a cave. Players who keep pulling
 * hidden ores out of solid rock at an impossible ratio get flagged.
 *
 * <p>This class is pure (no live server state) so it can be unit tested offline.
 */
public final class XrayStats {

    private XrayStats() {
    }

    /**
     * Hiddenness weight for an ore broken with the given count of exposed
     * (non-solid) neighbouring faces, 0..6.
     *
     * <ul>
     *   <li>0 exposed — mined through solid rock: 1.0</li>
     *   <li>1 exposed — just opened from a tunnel: 0.7</li>
     *   <li>2 exposed — edge of a cave: 0.3</li>
     *   <li>3+ exposed — clearly visible: 0.0</li>
     * </ul>
     */
    public static double hiddenness(int exposedFaces) {
        if (exposedFaces <= 0) {
            return 1.0;
        }
        if (exposedFaces == 1) {
            return 0.7;
        }
        if (exposedFaces == 2) {
            return 0.3;
        }
        return 0.0;
    }

    /**
     * Average hiddenness of the ores in a sample window.
     *
     * @param hiddenWeightSum sum of {@link #hiddenness} results
     * @param oreCount        number of ores in the window
     * @return 0.0 when the window has no ores
     */
    public static double averageHiddenness(double hiddenWeightSum, int oreCount) {
        if (oreCount <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, hiddenWeightSum / oreCount));
    }

    /**
     * Whether a mining sample is suspicious: enough ores were mined and their
     * average hiddenness reaches {@code minRatio}.
     */
    public static boolean suspicious(double hiddenWeightSum, int oreCount, double minRatio, int minSamples) {
        return oreCount >= minSamples && averageHiddenness(hiddenWeightSum, oreCount) >= minRatio;
    }

    /**
     * Violation level to raise for one suspicious window: scales with how far
     * the ratio exceeds the threshold and how big the sample is. Always at
     * least 1.0 and at most 10.0.
     */
    public static double violationLevel(double hiddenWeightSum, int oreCount, double minRatio, int minSamples) {
        double avg = averageHiddenness(hiddenWeightSum, oreCount);
        if (oreCount < minSamples || avg < minRatio) {
            return 0.0;
        }
        double excess = (avg - minRatio) / Math.max(0.01, 1.0 - minRatio);
        double sampleWeight = Math.min(2.0, 0.5 + oreCount / (double) Math.max(1, minSamples) * 0.5);
        return Math.max(1.0, Math.min(10.0, 1.0 + excess * 8.0 * sampleWeight));
    }
}
