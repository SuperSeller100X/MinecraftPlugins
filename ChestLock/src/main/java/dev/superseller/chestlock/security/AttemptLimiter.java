package dev.superseller.chestlock.security;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Per-player, per-lock failed-passcode throttling with bounded exponential cooldowns. */
public final class AttemptLimiter {
    private final ConcurrentMap<AttemptKey, State> states = new ConcurrentHashMap<>();

    public long remainingCooldown(UUID playerId, UUID lockId, long nowMillis) {
        State state = states.get(new AttemptKey(playerId, lockId));
        if (state == null || state.blockedUntilMillis <= nowMillis) {
            return 0;
        }
        return state.blockedUntilMillis - nowMillis;
    }

    public long recordFailure(
            UUID playerId,
            UUID lockId,
            long nowMillis,
            int attemptsBeforeCooldown,
            long attemptWindowMillis,
            long baseCooldownMillis,
            long maxCooldownMillis
    ) {
        AttemptKey key = new AttemptKey(playerId, lockId);
        State updated = states.compute(key, (ignored, previous) -> {
            State current = previous == null ? new State(0, nowMillis, 0, 0) : previous;
            if (current.blockedUntilMillis > nowMillis) {
                return current;
            }
            int failures = nowMillis - current.windowStartedMillis > attemptWindowMillis
                    ? 1 : current.failuresInWindow + 1;
            long windowStart = nowMillis - current.windowStartedMillis > attemptWindowMillis
                    ? nowMillis : current.windowStartedMillis;
            if (failures < attemptsBeforeCooldown) {
                return new State(failures, windowStart, current.cooldownLevel, 0);
            }
            int level = Math.min(30, current.cooldownLevel + 1);
            long multiplier = 1L << Math.min(20, level - 1);
            long cooldown;
            try {
                cooldown = Math.multiplyExact(baseCooldownMillis, multiplier);
            } catch (ArithmeticException exception) {
                cooldown = maxCooldownMillis;
            }
            cooldown = Math.min(maxCooldownMillis, cooldown);
            return new State(0, nowMillis, level, nowMillis + cooldown);
        });
        return Math.max(0, updated.blockedUntilMillis - nowMillis);
    }

    public void recordSuccess(UUID playerId, UUID lockId) {
        states.remove(new AttemptKey(playerId, lockId));
    }

    public void clearPlayer(UUID playerId) {
        states.keySet().removeIf(key -> key.playerId.equals(playerId));
    }

    private record AttemptKey(UUID playerId, UUID lockId) {
    }

    private record State(int failuresInWindow, long windowStartedMillis, int cooldownLevel, long blockedUntilMillis) {
    }
}
