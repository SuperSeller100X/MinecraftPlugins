package dev.superseller.veltryx.anticheat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player anti-cheat state for one session: violation levels per check,
 * movement/combat sample windows and the recent flag history shown in /sus.
 *
 * <p>Not thread-safe by design: it is only touched from the player's owning
 * region thread (events) plus the single global decay timer, which only calls
 * {@link #decayAll}.
 */
public final class PlayerTrace {

    /** One recorded flag (detection event) kept for /sus and ban evidence. */
    public record Flag(CheckType check, double vl, String detail, long issuedMillis) {
    }

    private static final int FLAG_HISTORY = 20;

    private final UUID uuid;
    private final String name;
    private final long joinedAtMillis;

    private final Map<CheckType, Double> violationLevels = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<CheckType, Long> lastAlertMillis = new java.util.concurrent.ConcurrentHashMap<>();
    private final ArrayDeque<Flag> flags = new ArrayDeque<>();

    // movement state
    private double lastX;
    private double lastY;
    private double lastZ;
    private long lastMoveNanos;
    private double airSeconds;
    private long lastFlySampleNanos;
    private final Map<CheckType, Long> lastMovementFlagNanos = new java.util.EnumMap<>(CheckType.class);
    private boolean positionInitialized;
    private boolean skipNextMove;
    private long jumpChainUntilNanos;
    private long speedGraceUntilMillis;
    private int speedExcessStreak;
    private long flyPullAtMillis;
    private int jesusStreak;
    private int phaseStreak;
    private long timerWindowStartNanos;
    private int timerWindowMoves;
    private int timerWindowAllowance;
    private int timerExcessWindows;

    // combat state
    private final ArrayDeque<Long> attackTimesMillis = new ArrayDeque<>();
    private final Map<UUID, Long> recentVictims = new LinkedHashMap<>();

    // interact state
    private final ArrayDeque<Long> breakTimesMillis = new ArrayDeque<>();
    private final ArrayDeque<Long> placeTimesMillis = new ArrayDeque<>();
    private final ArrayDeque<Long> consumeTimesMillis = new ArrayDeque<>();

    // xray mining window
    private double hiddenWeightSum;
    private int oreCount;

    public PlayerTrace(UUID uuid, String name, long joinedAtMillis) {
        this.uuid = uuid;
        this.name = name;
        this.joinedAtMillis = joinedAtMillis;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public long joinedAtMillis() {
        return joinedAtMillis;
    }

    // ----------------------------------------------------------- violations

    /** Current violation level of a check (0 when never flagged). */
    public double violationLevel(CheckType check) {
        return violationLevels.getOrDefault(check, 0.0);
    }

    /** Adds to a check's violation level and returns the new total. */
    public double addViolation(CheckType check, double amount) {
        double next = Math.max(0.0, violationLevel(check) + amount);
        violationLevels.put(check, Math.min(500.0, next));
        return violationLevel(check);
    }

    /** Sum of every violation level — the suspicion score for /sus. */
    public double totalViolationLevel() {
        double sum = 0.0;
        for (double value : violationLevels.values()) {
            sum += value;
        }
        return sum;
    }

    /** All current violation levels (live view for the GUI). */
    public Map<CheckType, Double> violationLevels() {
        return violationLevels;
    }

    /** Decays every violation level. Called by the global timer only. */
    public void decayAll(double perSecond, long nowMillis, long lastMillis) {
        double elapsedSeconds = Math.max(0.0, (nowMillis - lastMillis) / 1000.0);
        double decay = perSecond * elapsedSeconds;
        if (decay <= 0.0) {
            return;
        }
        for (CheckType check : List.copyOf(violationLevels.keySet())) {
            double next = violationLevel(check) - decay;
            if (next <= 0.0) {
                violationLevels.remove(check);
            } else {
                violationLevels.put(check, next);
            }
        }
    }

    /** Records a flag in the recent history (bounded). */
    public void addFlag(CheckType check, double vl, String detail, long nowMillis) {
        flags.addLast(new Flag(check, vl, detail, nowMillis));
        while (flags.size() > FLAG_HISTORY) {
            flags.removeFirst();
        }
    }

    /** Snapshot of the recent flags, newest first. */
    public List<Flag> recentFlags() {
        List<Flag> copy = new ArrayList<>(flags);
        java.util.Collections.reverse(copy);
        return copy;
    }

    /** Timestamp of the last alert for a check (for alert rate limiting). */
    public long lastAlertMillis(CheckType check) {
        return lastAlertMillis.getOrDefault(check, 0L);
    }

    public void markAlerted(CheckType check, long nowMillis) {
        lastAlertMillis.put(check, nowMillis);
    }

    // ----------------------------------------------------------- movement

    public boolean positionInitialized() {
        return positionInitialized;
    }

    /** Mark the start of a jump chain (sprint-hopping is the fastest legit movement). */
    public void markJump(long nowNanos) {
        jumpChainUntilNanos = nowNanos + 1_200_000_000L;
    }

    public boolean inJumpChain(long nowNanos) {
        return nowNanos < jumpChainUntilNanos;
    }

    /**
     * Suppress movement-checking briefly — used after teleports, server
     * authority rollbacks and corrections, where the next samples are
     * artifacts and must never count toward any violation level.
     */
    public void setSpeedGraceUntilMillis(long untilMillis) {
        speedGraceUntilMillis = untilMillis;
    }

    public boolean hasSpeedGrace(long nowMillis) {
        return nowMillis < speedGraceUntilMillis;
    }

    /** Consecutive movement samples above the speed tolerance. */
    public int speedExcessStreak() {
        return speedExcessStreak;
    }

    public void setSpeedExcessStreak(int streak) {
        speedExcessStreak = streak;
    }

    /** Throttles the smooth fly gravity pull to at most one per 500 ms. */
    public long flyPullAtMillis() {
        return flyPullAtMillis;
    }

    public void setFlyPullAtMillis(long millis) {
        flyPullAtMillis = millis;
    }

    /** Consecutive surface-skimming samples (jesus check). */
    public int jesusStreak() {
        return jesusStreak;
    }

    public void setJesusStreak(int streak) {
        jesusStreak = streak;
    }

    public void recordPosition(double x, double y, double z, long nowNanos) {
        lastX = x;
        lastY = y;
        lastZ = z;
        lastMoveNanos = nowNanos;
        positionInitialized = true;
    }

    public double lastX() {
        return lastX;
    }

    public double lastY() {
        return lastY;
    }

    public double lastZ() {
        return lastZ;
    }

    /** Seconds since the previous recorded move (0.0 on the first move). */
    public double secondsSinceLastMove(long nowNanos) {
        if (lastMoveNanos == 0L) {
            return 0.0;
        }
        return Math.max(0.0, (nowNanos - lastMoveNanos) / 1_000_000_000.0);
    }

    /** Elapsed time since the last sample used by the fly check. */
    public double secondsSinceLastFlySample(long nowNanos) {
        if (lastFlySampleNanos == 0L) {
            return 0.0;
        }
        return Math.max(0.0, (nowNanos - lastFlySampleNanos) / 1_000_000_000.0);
    }

    public void touchFlySample(long nowNanos) {
        lastFlySampleNanos = nowNanos;
    }

    public double airSeconds() {
        return airSeconds;
    }

    public void setAirSeconds(double seconds) {
        airSeconds = Double.isFinite(seconds) ? Math.max(0.0, seconds) : 0.0;
    }

    /** Limit repeated movement flags for the same check to at most two per second. */
    public boolean shouldFlagMovement(CheckType check, long nowNanos) {
        Long previous = lastMovementFlagNanos.get(check);
        if (previous != null && nowNanos - previous < 500_000_000L) {
            return false;
        }
        lastMovementFlagNanos.put(check, nowNanos);
        return true;
    }

    public int phaseStreak() {
        return phaseStreak;
    }

    public void setPhaseStreak(int streak) {
        phaseStreak = Math.max(0, streak);
    }

    /** Clear evidence that cannot be carried across a change in physics. */
    public void resetPhysicsCounters() {
        airSeconds = 0.0;
        speedExcessStreak = 0;
        jumpChainUntilNanos = 0L;
    }

    /** Clear all movement evidence when samples are unreliable or unavailable. */
    public void resetMovementChecks() {
        resetPhysicsCounters();
        jesusStreak = 0;
        phaseStreak = 0;
        timerWindowStartNanos = 0L;
        timerWindowMoves = 0;
        timerWindowAllowance = 0;
        timerExcessWindows = 0;
    }

    /** Marks the next move event as trusted (after teleports, respawns, …). */
    public void skipNextMove() {
        resetMovementChecks();
        skipNextMove = true;
    }

    public boolean consumeSkipNextMove() {
        boolean skip = skipNextMove;
        skipNextMove = false;
        return skip;
    }

    /**
     * Record a positional update for the timer check. A single packet burst is
     * not evidence: two consecutive completed windows must exceed their
     * largest allowance per second. Windows are evaluated on the next move,
     * so their thresholds are scaled by their actual duration. Returns the
     * completed window's rounded updates per second if flagged.
     */
    public int recordTimerMove(long nowNanos, int allowance) {
        if (timerWindowStartNanos == 0L) {
            timerWindowStartNanos = nowNanos;
        }
        long elapsed = nowNanos - timerWindowStartNanos;
        int flaggedRate = 0;
        if (elapsed < 0L || elapsed >= 1_000_000_000L) {
            if (elapsed >= 1_000_000_000L && elapsed < 2_000_000_000L
                    && timerWindowMoves > timerWindowAllowance * (elapsed / 1_000_000_000.0)) {
                timerExcessWindows++;
                if (timerExcessWindows >= 2) {
                    flaggedRate = (int) Math.round(timerWindowMoves / (elapsed / 1_000_000_000.0));
                }
            } else {
                timerExcessWindows = 0;
            }
            timerWindowStartNanos = nowNanos;
            timerWindowMoves = 0;
            timerWindowAllowance = 0;
        }
        timerWindowMoves++;
        timerWindowAllowance = Math.max(timerWindowAllowance, allowance);
        return flaggedRate;
    }

    // ------------------------------------------------------------ combat

    /** Records an attack timestamp; returns attacks in the last second. */
    public int pushAttack(long nowMillis) {
        attackTimesMillis.addLast(nowMillis);
        pruneWindow(attackTimesMillis, nowMillis, 1000L);
        return attackTimesMillis.size();
    }

    /** Records a hit victim; returns distinct victims in the last 1.5 s. */
    public int pushVictim(UUID victim, long nowMillis) {
        recentVictims.put(victim, nowMillis);
        recentVictims.values().removeIf(t -> nowMillis - t > 1500L);
        return recentVictims.size();
    }

    /** Intervals (ms) between the recorded attacks, oldest first. */
    public List<Long> attackIntervals() {
        List<Long> times = new ArrayList<>(attackTimesMillis);
        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < times.size(); i++) {
            intervals.add(times.get(i) - times.get(i - 1));
        }
        return intervals;
    }

    // ---------------------------------------------------------- interact

    /** Records a block break; returns breaks in the last second. */
    public int pushBreak(long nowMillis) {
        breakTimesMillis.addLast(nowMillis);
        pruneWindow(breakTimesMillis, nowMillis, 1000L);
        return breakTimesMillis.size();
    }

    /** Records a block place; returns places in the last second. */
    public int pushPlace(long nowMillis) {
        placeTimesMillis.addLast(nowMillis);
        pruneWindow(placeTimesMillis, nowMillis, 1000L);
        return placeTimesMillis.size();
    }

    /** Records an item consume; returns the gap in ms to the previous consume. */
    public long pushConsume(long nowMillis) {
        Long previous = consumeTimesMillis.peekLast();
        consumeTimesMillis.addLast(nowMillis);
        pruneWindow(consumeTimesMillis, nowMillis, 5000L);
        return previous == null ? Long.MAX_VALUE : nowMillis - previous;
    }

    // -------------------------------------------------------------- xray

    /** Adds one mined ore to the current sample window. */
    public void addOreSample(double hiddenness) {
        hiddenWeightSum += hiddenness;
        oreCount++;
    }

    public double hiddenWeightSum() {
        return hiddenWeightSum;
    }

    public int oreCount() {
        return oreCount;
    }

    /** Clears the mining sample window (after it has been evaluated). */
    public void clearOreWindow() {
        hiddenWeightSum = 0.0;
        oreCount = 0;
    }

    // ------------------------------------------------------------ helper

    private static void pruneWindow(ArrayDeque<Long> window, long nowMillis, long widthMillis) {
        while (!window.isEmpty() && nowMillis - window.peekFirst() > widthMillis) {
            window.removeFirst();
        }
    }
}
