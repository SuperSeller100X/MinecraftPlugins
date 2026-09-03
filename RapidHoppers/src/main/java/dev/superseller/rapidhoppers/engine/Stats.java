package dev.superseller.rapidhoppers.engine;

import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe live counters shown by {@code /rh stats} and the GUI. */
public final class Stats {

    private final AtomicLong totalTransfers = new AtomicLong();
    private final AtomicLong totalItems = new AtomicLong();
    private final AtomicLong windowTransfers = new AtomicLong();
    private volatile long windowStart = System.nanoTime();
    private volatile double lastRate;
    private volatile int trackedContainers;

    public void recordTransfer(int items) {
        totalTransfers.incrementAndGet();
        totalItems.addAndGet(Math.max(0, items));
        windowTransfers.incrementAndGet();
    }

    /** Recomputes the per-second rate; call roughly once per second. */
    public void tickWindow() {
        long now = System.nanoTime();
        double seconds = (now - windowStart) / 1_000_000_000.0D;
        if (seconds <= 0.0D) {
            return;
        }
        lastRate = TransferMath.round1(windowTransfers.getAndSet(0L) / seconds);
        windowStart = now;
    }

    public long totalTransfers() {
        return totalTransfers.get();
    }

    public long totalItems() {
        return totalItems.get();
    }

    public double ratePerSecond() {
        return lastRate;
    }

    public int trackedContainers() {
        return trackedContainers;
    }

    public void setTrackedContainers(int trackedContainers) {
        this.trackedContainers = trackedContainers;
    }

    public void reset() {
        totalTransfers.set(0L);
        totalItems.set(0L);
        windowTransfers.set(0L);
        windowStart = System.nanoTime();
        lastRate = 0.0D;
    }
}
