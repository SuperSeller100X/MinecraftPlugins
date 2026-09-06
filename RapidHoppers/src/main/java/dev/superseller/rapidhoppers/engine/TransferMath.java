package dev.superseller.rapidhoppers.engine;

import dev.superseller.rapidhoppers.config.Settings;

/**
 * Pure, side-effect-free math used by the transfer engine.
 *
 * <p>Kept free of Bukkit types on purpose so it can be unit tested without a
 * running server (see {@code smoke/EngineTest.java}).</p>
 */
public final class TransferMath {

    private TransferMath() {
    }

    /**
     * Effective interval in ticks, taking the throttle state into account.
     *
     * @param base         configured base interval (1-8)
     * @param throttled    whether the TPS throttle is active
     * @param throttleMult throttle interval multiplier
     * @return interval in ticks, never below 1 and never above vanilla's 8
     */
    public static int effectiveInterval(int base, boolean throttled, int throttleMult) {
        int interval = Math.max(1, base);
        if (throttled) {
            interval *= Math.max(1, throttleMult);
        }
        return Math.min(Settings.VANILLA_INTERVAL_TICKS, Math.max(1, interval));
    }

    /**
     * Speed relative to a vanilla hopper.
     *
     * <p>RapidHoppers never changes <em>how much</em> moves — always exactly one
     * item, exactly like vanilla — only how often. A hopper that moves one item
     * every {@code intervalTicks} instead of every 8 ticks is
     * {@code 8 / intervalTicks} times as fast.</p>
     */
    public static double speedFactor(int intervalTicks) {
        int interval = Math.max(1, Math.min(Settings.VANILLA_INTERVAL_TICKS, intervalTicks));
        return (double) Settings.VANILLA_INTERVAL_TICKS / interval;
    }

    /** Items moved per second at the given interval (vanilla: 2.5). */
    public static double itemsPerSecond(int intervalTicks) {
        int interval = Math.max(1, Math.min(Settings.VANILLA_INTERVAL_TICKS, intervalTicks));
        return 20.0D / interval;
    }

    /**
     * How many items may actually move in one operation.
     *
     * <p>A hopper transfer is always a single item, so {@code requested} is
     * clamped to 1 as well; the parameter exists so callers can pass 0 to mean
     * "nothing".</p>
     *
     * @param requested   items the caller would like to move (clamped to 1)
     * @param available   items present in the source slot
     * @param destination free space in the destination for that item type
     */
    public static int moveAmount(int requested, int available, int destination) {
        int amount = Math.max(0, Math.min(requested, Settings.ITEMS_PER_TRANSFER));
        amount = Math.min(amount, Math.max(0, available));
        amount = Math.min(amount, Math.max(0, destination));
        return Math.max(0, amount);
    }

    /**
     * True when a container is allowed to move again.
     *
     * <p>Mirrors the vanilla hopper cooldown, but with the configured interval
     * instead of the hard-coded 8 ticks. Vanilla's own transfers feed the same
     * clock, so the plugin tops a hopper up to the configured rate instead of
     * adding its throughput on top of vanilla's.</p>
     *
     * @param now      current tick
     * @param lastMove tick of the last observed transfer ({@code Long.MIN_VALUE} for never)
     * @param interval configured interval in ticks
     */
    public static boolean isReady(long now, long lastMove, int interval) {
        if (lastMove == Long.MIN_VALUE) {
            return true;
        }
        return now - lastMove >= Math.max(1, interval);
    }

    /** True when the throttle should engage. */
    public static boolean shouldThrottle(double tps, double soft, boolean enabled) {
        return enabled && tps < soft;
    }

    /** True when the engine should pause entirely. */
    public static boolean shouldPause(double tps, double hard, boolean enabled) {
        return enabled && tps < hard;
    }

    /** Chebyshev chunk distance between two chunk coordinates. */
    public static int chunkDistance(int ax, int az, int bx, int bz) {
        return Math.max(Math.abs(ax - bx), Math.abs(az - bz));
    }

    /** Rounds a rate to one decimal, avoiding locale-dependent formatting. */
    public static double round1(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }
}
