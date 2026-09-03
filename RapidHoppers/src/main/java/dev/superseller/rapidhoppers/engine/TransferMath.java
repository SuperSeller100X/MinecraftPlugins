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
     * Effective interval in ticks for a container family, taking the throttle
     * state and per-type multipliers into account.
     *
     * @param base         configured base interval (1-8)
     * @param typeFactor   per-type multiplier (dropper/dispenser)
     * @param throttled    whether the TPS throttle is active
     * @param throttleMult throttle interval multiplier
     * @return interval in ticks, never below 1
     */
    public static int effectiveInterval(int base, int typeFactor, boolean throttled, int throttleMult) {
        int interval = Math.max(1, base) * Math.max(1, typeFactor);
        if (throttled) {
            interval *= Math.max(1, throttleMult);
        }
        return Math.max(1, interval);
    }

    /** Speed relative to vanilla hoppers (8 ticks per item). */
    public static double speedFactor(int intervalTicks, int itemsPerTransfer) {
        int interval = Math.max(1, intervalTicks);
        int amount = Math.max(1, itemsPerTransfer);
        return ((double) Settings.VANILLA_INTERVAL_TICKS / interval) * amount;
    }

    /**
     * How many items may actually move in one operation.
     *
     * @param requested   configured items-per-transfer
     * @param available   items present in the source slot
     * @param destination free space in the destination for that item type
     * @param maxStack    the item's maximum stack size
     */
    public static int moveAmount(int requested, int available, int destination, int maxStack) {
        int amount = Math.max(0, Math.min(requested, available));
        amount = Math.min(amount, Math.max(0, destination));
        amount = Math.min(amount, Math.max(1, maxStack));
        return Math.max(0, amount);
    }

    /**
     * True when the throttle should engage.
     *
     * @param tps  measured TPS
     * @param soft soft threshold - below this the engine slows down
     */
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
