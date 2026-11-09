package dev.superseller.veltryx.anticheat;

/**
 * Ping and TPS awareness for the anti-cheat.
 *
 * <p>Every check scales its tolerances through {@link #allowanceFactor} so
 * laggy players and laggy servers get more leeway, and {@link #reliable}
 * gates automatic punishments behind a healthy connection and server.
 *
 * <p>This class is pure (no live server state) so it can be unit tested offline.
 */
public final class PingGuard {

    private PingGuard() {
    }

    /**
     * Multiplier applied to movement/combat tolerances.
     *
     * <p>Starts at 1.0 and grows by 12% per 100 ms of ping above 50 ms
     * (capped at 2.2×), then scales up further when the server TPS drops
     * below 20 (capped so the combined factor never exceeds 3.0×).
     */
    public static double allowanceFactor(int pingMillis, double tps) {
        double pingFactor = 1.0 + Math.max(0, pingMillis - 50) / 100.0 * 0.12;
        pingFactor = Math.min(2.2, pingFactor);
        double tpsFactor = 1.0;
        if (tps > 0 && tps < 20.0) {
            tpsFactor = Math.min(1.4, 20.0 / Math.max(1.0, tps));
        }
        return Math.min(3.0, pingFactor * tpsFactor);
    }

    /**
     * Whether detections should run at all. Checks are skipped while the
     * connection is worse than {@code maxPingMillis} or the server slower
     * than {@code minTps} — flagging on garbage data only causes false bans.
     */
    public static boolean shouldSkip(int pingMillis, double tps, int maxPingMillis, double minTps) {
        return pingMillis > maxPingMillis || (tps > 0 && tps < minTps);
    }

    /**
     * Whether automatic <b>punishments</b> (kick/ban) may be applied right now.
     * Stricter than {@link #shouldSkip}: flags are still recorded on a bumpy
     * connection, but kicks and bans wait for healthy conditions.
     */
    public static boolean reliable(int pingMillis, double tps, int punishMaxPingMillis, double punishMinTps) {
        return pingMillis <= punishMaxPingMillis && (tps <= 0 || tps >= punishMinTps);
    }
}
