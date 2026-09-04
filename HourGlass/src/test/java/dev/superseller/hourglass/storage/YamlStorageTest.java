package dev.superseller.hourglass.storage;

import java.util.List;
import java.util.UUID;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.superseller.hourglass.data.PlaytimeRecord;
import dev.superseller.hourglass.data.Session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * YAML is the only storage backend, so a record must survive the round trip
 * through real text. These tests use Bukkit's {@link YamlConfiguration} parser
 * but no running server.
 */
class YamlStorageTest {

    @Test
    @DisplayName("a record survives save -> load unchanged")
    void roundTrip() throws Exception {
        UUID id = UUID.fromString("0cb6c3f0-4b9a-4c0a-8f3a-4c1f2a3b4d5e");
        PlaytimeRecord record = new PlaytimeRecord(id, "Notch");
        record.loadFrom(4_200L, 3_600L, 1_700_000_000_000L, 1_700_000_900_000L,
                List.of(new Session(1_700_000_000_000L, 1_700_000_900_000L, 900L, 800L),
                        new Session(1_699_000_000_000L, 1_699_500_000L, 500L, 400L)),
                List.of("one-hour", "first-join"), true, PlaytimeRecord.DisplayMode.ACTIONBAR);

        String text = YamlPlayerStorage.toYaml(record).saveToString();
        YamlConfiguration read = new YamlConfiguration();
        read.loadFromString(text);

        PlaytimeRecord restored = new PlaytimeRecord(id, "Notch");
        YamlPlayerStorage.apply(restored, read);

        assertEquals("Notch", restored.name());
        assertEquals(4_200L, restored.totalSeconds());
        assertEquals(3_600L, restored.activeSeconds());
        assertEquals(1_700_000_000_000L, restored.firstJoin());
        assertEquals(1_700_000_900_000L, restored.lastSeen());
        assertTrue(restored.frozen());
        assertEquals(PlaytimeRecord.DisplayMode.ACTIONBAR, restored.display());
        assertEquals(2, restored.historyCount());
        assertEquals(900L, restored.history().get(0).totalSeconds());
        assertEquals(400L, restored.history().get(1).activeSeconds());
        assertEquals(List.of("first-join", "one-hour"), restored.awardedList().stream().sorted().toList());
        assertFalse(restored.dirty(), "a freshly loaded record is in sync with the file");
    }

    @Test
    @DisplayName("an empty record stays small and loads back as zeroes")
    void emptyRecord() throws Exception {
        PlaytimeRecord record = new PlaytimeRecord(UUID.randomUUID(), "Fresh");
        String text = YamlPlayerStorage.toYaml(record).saveToString();
        assertFalse(text.contains("history:"), "no history key when there are no sessions: " + text);
        assertFalse(text.contains("milestones:"));

        PlaytimeRecord restored = new PlaytimeRecord(record.id(), null);
        restored.historySize(5);
        YamlConfiguration read = new YamlConfiguration();
        read.loadFromString(text);
        YamlPlayerStorage.apply(restored, read);
        assertEquals(0L, restored.totalSeconds());
        assertEquals(0L, restored.historyCount());
        assertEquals("Fresh", restored.name(), "the name from the file wins when the record had none");
    }

    @Test
    @DisplayName("a damaged file never throws and never inflates the totals")
    void damaged() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 1);
        yaml.set("total-seconds", "not a number");
        yaml.set("active-seconds", -50);
        yaml.set("frozen", "yes please");
        yaml.set("display", "hologram");
        yaml.set("history", "not a list at all");
        PlaytimeRecord record = new PlaytimeRecord(UUID.randomUUID(), "Bob");
        YamlPlayerStorage.apply(record, yaml);
        assertEquals(0L, record.totalSeconds());
        assertEquals(0L, record.activeSeconds());
        assertFalse(record.frozen());
        assertEquals(PlaytimeRecord.DisplayMode.INHERIT, record.display());
        assertEquals(0, record.historyCount());
    }

    @Test
    @DisplayName("future schema versions still load what they contain")
    void futureSchema() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 99);
        yaml.set("total-seconds", 120L);
        yaml.set("unknown-future-key", "ignored");
        PlaytimeRecord record = new PlaytimeRecord(UUID.randomUUID(), "Bob");
        YamlPlayerStorage.apply(record, yaml);
        assertEquals(120L, record.totalSeconds());
    }

    @Test
    @DisplayName("file names are the player's uuid, which every OS accepts")
    void fileNames() {
        UUID id = UUID.fromString("95998b5e-2005-4fa2-9b56-3f2e1b0e2b7a");
        assertEquals(id, YamlPlayerStorage.parseName(id + ".yml"));
        assertEquals(id, YamlPlayerStorage.parseName(id.toString().toUpperCase(java.util.Locale.US) + ".YML"),
                "case is preserved by the OS but parsing stays tolerant");
        assertNull(YamlPlayerStorage.parseName("readme.txt"));
        assertNull(YamlPlayerStorage.parseName(""));
        assertNull(YamlPlayerStorage.parseName(null));
        assertEquals(36, id.toString().length(), "no illegal characters, no separators");
        assertFalse(id.toString().contains(":"), "a colon would break Windows paths");
    }

    @Test
    @DisplayName("negative or missing timestamps round-trip as zero")
    void negativeTimestamps() {
        PlaytimeRecord record = new PlaytimeRecord(UUID.randomUUID(), "Bob");
        record.loadFrom(10L, 5L, -7L, 0L, List.of(new Session(5L, -1L, 3L, -2L)), List.of(), false,
                PlaytimeRecord.DisplayMode.INHERIT);
        YamlConfiguration yaml = YamlPlayerStorage.toYaml(record);
        PlaytimeRecord restored = new PlaytimeRecord(record.id(), null);
        YamlPlayerStorage.apply(restored, yaml);
        assertEquals(0L, restored.firstJoin());
        assertEquals(0L, restored.lastSeen());
        assertEquals(1, restored.historyCount());
        assertEquals(-1L, restored.history().get(0).end(), "an unfinished session stays marked as running");
        assertTrue(restored.history().get(0).running());
        assertEquals(0L, restored.history().get(0).activeSeconds());
    }
}
