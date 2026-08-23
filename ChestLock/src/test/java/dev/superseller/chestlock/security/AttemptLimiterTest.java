package dev.superseller.chestlock.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttemptLimiterTest {
    @Test
    void appliesAndEscalatesCooldownThenClearsOnSuccess() {
        AttemptLimiter limiter = new AttemptLimiter();
        UUID player = UUID.randomUUID();
        UUID lock = UUID.randomUUID();
        long now = 1_000_000L;

        assertEquals(0, failure(limiter, player, lock, now));
        assertEquals(0, failure(limiter, player, lock, now + 1));
        assertEquals(5_000, failure(limiter, player, lock, now + 2));
        assertEquals(4_000, limiter.remainingCooldown(player, lock, now + 1_002));

        long afterFirst = now + 5_003;
        assertEquals(0, failure(limiter, player, lock, afterFirst));
        assertEquals(0, failure(limiter, player, lock, afterFirst + 1));
        assertEquals(10_000, failure(limiter, player, lock, afterFirst + 2));

        limiter.recordSuccess(player, lock);
        assertEquals(0, limiter.remainingCooldown(player, lock, afterFirst + 3));
    }

    @Test
    void isolatesPlayersAndLocksAndCapsCooldown() {
        AttemptLimiter limiter = new AttemptLimiter();
        UUID player = UUID.randomUUID();
        UUID firstLock = UUID.randomUUID();
        UUID secondLock = UUID.randomUUID();
        long now = 50_000L;

        for (int round = 0; round < 8; round++) {
            failure(limiter, player, firstLock, now);
            failure(limiter, player, firstLock, now + 1);
            long cooldown = failure(limiter, player, firstLock, now + 2);
            assertTrue(cooldown <= 20_000);
            now += cooldown + 3;
        }
        assertEquals(0, limiter.remainingCooldown(player, secondLock, now));
    }

    private static long failure(AttemptLimiter limiter, UUID player, UUID lock, long now) {
        return limiter.recordFailure(player, lock, now, 3, 30_000, 5_000, 20_000);
    }
}
