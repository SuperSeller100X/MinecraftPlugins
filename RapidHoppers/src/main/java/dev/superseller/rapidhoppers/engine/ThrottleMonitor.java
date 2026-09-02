package dev.superseller.rapidhoppers.engine;

import dev.superseller.rapidhoppers.config.Settings;
import dev.superseller.rapidhoppers.scheduler.PlatformScheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Watches server TPS and reports whether the engine should slow down or pause.
 *
 * <p>TPS is read reflectively from {@code Bukkit#getTPS()} (Paper/Purpur/Folia
 * all expose it); if the call is unavailable the monitor measures wall-clock
 * time between its own runs, so it still works on any implementation.</p>
 */
public final class ThrottleMonitor {

    /** Throttle state. */
    public enum State {
        NORMAL,
        THROTTLED,
        PAUSED
    }

    private final JavaPlugin plugin;
    private final Settings settings;

    private PlatformScheduler.Handle task;
    private volatile State state = State.NORMAL;
    private volatile double tps = 20.0D;

    private long lastRun = System.nanoTime();
    private long lastTicks;

    public ThrottleMonitor(JavaPlugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public void start() {
        stop();
        long period = Math.max(1, settings.getThrottleCheckSeconds()) * 20L;
        task = PlatformScheduler.runTimer(this::check, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void check() {
        tps = measureTps();
        State next;
        if (TransferMath.shouldPause(tps, settings.getThrottleHardTps(), settings.isThrottleEnabled())) {
            next = State.PAUSED;
        } else if (TransferMath.shouldThrottle(tps, settings.getThrottleSoftTps(), settings.isThrottleEnabled())) {
            next = State.THROTTLED;
        } else {
            next = State.NORMAL;
        }
        if (next != state) {
            State previous = state;
            state = next;
            if (settings.isThrottleLogChanges()) {
                plugin.getLogger().info("Throttle state " + previous + " -> " + next
                        + " (TPS " + TransferMath.round1(tps) + ")");
            }
        }
    }

    private double measureTps() {
        try {
            double[] values = (double[]) Bukkit.class.getMethod("getTPS").invoke(null);
            if (values != null && values.length > 0 && values[0] > 0.0D) {
                return Math.min(20.0D, values[0]);
            }
        } catch (Throwable ignored) {
            // fall through to wall-clock estimation
        }
        long now = System.nanoTime();
        double seconds = (now - lastRun) / 1_000_000_000.0D;
        lastRun = now;
        long expected = Math.max(1L, settings.getThrottleCheckSeconds());
        lastTicks++;
        if (seconds <= 0.0D) {
            return 20.0D;
        }
        return Math.min(20.0D, 20.0D * (expected / seconds));
    }

    public State state() {
        return state;
    }

    public boolean isPaused() {
        return state == State.PAUSED;
    }

    public boolean isThrottled() {
        return state == State.THROTTLED;
    }

    public double tps() {
        return TransferMath.round1(tps);
    }

    /** Total number of monitor runs; used by diagnostics and tests. */
    public long checks() {
        return lastTicks;
    }
}
