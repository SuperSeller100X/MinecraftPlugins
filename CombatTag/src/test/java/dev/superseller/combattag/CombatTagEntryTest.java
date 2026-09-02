package dev.superseller.combattag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.superseller.combattag.combat.CombatTagEntry;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CombatTagEntryTest {

    @Test
    @DisplayName("remaining time, progress and expiry are consistent")
    void lifecycle() {
        long now = 1_000_000L;
        CombatTagEntry entry = new CombatTagEntry(UUID.randomUUID(), null, now + 10_000L, now, 10_000L);

        assertFalse(entry.isExpired(now));
        assertEquals(10, entry.remainingSeconds(now));
        assertEquals(1.0F, entry.progress(now), 0.001F);
        assertEquals(0.5F, entry.progress(now + 5_000L), 0.001F);
        assertTrue(entry.isExpired(now + 10_000L));
        assertEquals(0L, entry.remainingMillis(now + 20_000L));
        assertEquals(0.0F, entry.progress(now + 20_000L), 0.001F);
    }
}
