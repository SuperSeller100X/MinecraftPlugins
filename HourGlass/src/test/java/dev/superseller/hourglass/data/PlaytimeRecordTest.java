package dev.superseller.hourglass.data;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The accounting core: what is added, what is clamped, what survives an admin
 * edit and what a session closes with. This is the maths the whole plugin is
 * built on, so it is tested without a server.
 */
class PlaytimeRecordTest {

    private static PlaytimeRecord record() {
        return new PlaytimeRecord(UUID.randomUUID(), "Tester");
    }

    @Test
    @DisplayName("accumulating milliseconds becomes whole seconds")
    void accumulate() {
        PlaytimeRecord record = record();
        record.accumulate(2_500L, true);
        assertEquals(2L, record.totalSeconds());
        assertEquals(2L, record.activeSeconds());
        record.accumulate(500L, false);
        assertEquals(3L, record.totalSeconds());
        assertEquals(2L, record.activeSeconds(), "idle time extends the total but not the active counter");
    }

    @Test
    @DisplayName("no time is counted for a frozen or untimed player by the caller")
    void frozenStaysPut() {
        PlaytimeRecord record = record();
        record.accumulate(60_000L, true);
        record.frozen(true);
        assertTrue(record.frozen());
        assertTrue(record.dirty(), "a freeze must be written to disk");
    }

    @Test
    @DisplayName("idle detection uses the configured window")
    void idle() {
        PlaytimeRecord record = record();
        long now = 1_000_000L;
        record.markActivity(now);
        assertFalse(record.isIdle(now + 60_000L, 300_000L));
        assertTrue(record.isIdle(now + 301_000L, 300_000L));
        assertFalse(record.isIdle(now + 10_000_000L, 0L), "idle-seconds: 0 disables the check");
    }

    @Test
    @DisplayName("admin set clamps active time below the new total")
    void setTotal() {
        PlaytimeRecord record = record();
        record.accumulate(10_000_000L, true); // total 10000 s, active 10000 s
        record.setTotalSeconds(100L);
        assertEquals(100L, record.totalSeconds());
        assertEquals(100L, record.activeSeconds(), "active can never exceed total");
    }

    @Test
    @DisplayName("remove never goes below zero")
    void shiftDown() {
        PlaytimeRecord record = record();
        record.setTotalSeconds(60L);
        record.shiftSeconds(-120L, true);
        assertEquals(0L, record.totalSeconds());
        assertEquals(0L, record.activeSeconds());
    }

    @Test
    @DisplayName("admin grants can skip the active counter")
    void shiftActive() {
        PlaytimeRecord record = record();
        record.setTotalSeconds(60L);
        record.shiftSeconds(60L, false);
        assertEquals(120L, record.totalSeconds());
        assertEquals(0L, record.activeSeconds());
        record.shiftSeconds(60L, true);
        assertEquals(60L, record.activeSeconds());
    }

    @Test
    @DisplayName("sessions are recorded on logout and capped by history-size")
    void history() {
        PlaytimeRecord record = record();
        record.historySize(2);
        long base = 1_700_000_000_000L;
        for (int i = 0; i < 5; i++) {
            record.startSession(base + i * 10_000L);
            record.accumulate(5_000L, true);
            record.endSession(base + i * 10_000L + 5_000L);
        }
        assertEquals(2, record.historyCount());
        List<Session> history = record.history();
        assertEquals(5L, history.get(0).totalSeconds());
        assertEquals(5L, history.get(1).totalSeconds());
        assertTrue(history.get(0).start() > history.get(1).start(), "newest first");
        assertFalse(record.online());
        assertEquals(base + 4 * 10_000L + 5_000L, record.lastSeen());
    }

    @Test
    @DisplayName("history-size 0 keeps no sessions at all")
    void historyDisabled() {
        PlaytimeRecord record = record();
        record.historySize(0);
        record.startSession(1_000L);
        record.accumulate(10_000L, true);
        record.endSession(11_000L);
        assertEquals(0, record.historyCount());
        assertEquals(10L, record.totalSeconds(), "the totals are still tracked");
    }

    @Test
    @DisplayName("session counters run while online and freeze at logout")
    void sessionCounters() {
        PlaytimeRecord record = record();
        record.startSession(10_000L);
        assertTrue(record.online());
        record.accumulate(30_000L, true);
        assertEquals(30L, record.sessionTotalSeconds(40_000L));
        record.endSession(40_000L);
        assertFalse(record.online());
        assertEquals(30L, record.sessionTotalSeconds(99_000L), "the last session is still reported");
        assertEquals(30L, record.sessionActiveSeconds());
    }

    @Test
    @DisplayName("admin edits re-baseline the open session")
    void editDuringSession() {
        PlaytimeRecord record = record();
        record.startSession(10_000L);
        record.accumulate(10_000L, true);
        record.setTotalSeconds(1_000L);
        assertEquals(0L, record.sessionTotalSeconds(20_000L), "a set must not inflate the live session");
        record.shiftSeconds(50L, true);
        assertEquals(1_050L, record.totalSeconds());
    }

    @Test
    @DisplayName("elapsed measurement is monotonic and never negative")
    void measurement() {
        PlaytimeRecord record = record();
        record.resetMeasurement();
        assertTrue(record.measureElapsed() >= 0L);
        record.startSession(System.currentTimeMillis());
        assertTrue(record.measureElapsed() >= 0L);
        assertTrue(record.measureElapsed() < 60_000L, "a tick can never invent a minute of playtime");
    }

    @Test
    @DisplayName("milestones are awarded exactly once")
    void milestones() {
        PlaytimeRecord record = record();
        assertTrue(record.award("one-day"));
        assertFalse(record.award("one-day"));
        assertEquals(List.of("one-day"), record.awardedList());
        assertTrue(record.awardedMilestones().contains("one-day"));
        record.reset(true);
        assertTrue(record.awardedMilestones().isEmpty());
        assertTrue(record.firstJoin() == 0L);
    }

    @Test
    @DisplayName("reset keeps identity, optionally the first join")
    void reset() {
        PlaytimeRecord record = record();
        record.loadFrom(500L, 400L, 123L, 456L, List.of(), List.of("x"), true, PlaytimeRecord.DisplayMode.BOSSBAR);
        record.reset(true);
        assertEquals(0L, record.totalSeconds());
        assertEquals(0L, record.activeSeconds());
        assertEquals(123L, record.firstJoin(), "a reset must not pretend the player is new");
        assertFalse(record.frozen());
        assertEquals(PlaytimeRecord.DisplayMode.BOSSBAR, record.display());
    }

    @Test
    @DisplayName("display modes parse the config spellings")
    void displayModes() {
        assertEquals(PlaytimeRecord.DisplayMode.BOSSBAR, PlaytimeRecord.DisplayMode.parse("bossbar"));
        assertEquals(PlaytimeRecord.DisplayMode.BOSSBAR, PlaytimeRecord.DisplayMode.parse("BOSS"));
        assertEquals(PlaytimeRecord.DisplayMode.ACTIONBAR, PlaytimeRecord.DisplayMode.parse("action"));
        assertEquals(PlaytimeRecord.DisplayMode.OFF, PlaytimeRecord.DisplayMode.parse("none"));
        assertEquals(PlaytimeRecord.DisplayMode.INHERIT, PlaytimeRecord.DisplayMode.parse("whatever"));
        assertEquals(PlaytimeRecord.DisplayMode.INHERIT, PlaytimeRecord.DisplayMode.parse(null));
    }

    @Test
    @DisplayName("snapshot is a consistent copy")
    void snapshot() {
        PlaytimeRecord record = record();
        record.loadFrom(60L, 30L, 10L, 20L, List.of(new Session(1L, 2L, 3L, 4L)), List.of("a"), false,
                PlaytimeRecord.DisplayMode.OFF);
        PlaytimeRecord.Snapshot snapshot = record.snapshot(5_000L);
        assertEquals(60L, snapshot.totalSeconds());
        assertEquals(30L, snapshot.activeSeconds());
        assertEquals("Tester", snapshot.displayName());
        assertEquals(1, snapshot.history().size());
        assertFalse(snapshot.online());
        assertEquals(PlaytimeRecord.metricSeconds(snapshot, "active"), 30L);
        assertEquals(PlaytimeRecord.metricSeconds(snapshot, "total"), 60L);
    }
}
