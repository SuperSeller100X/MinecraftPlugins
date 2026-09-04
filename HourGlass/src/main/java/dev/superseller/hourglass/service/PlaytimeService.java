package dev.superseller.hourglass.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.superseller.hourglass.HourGlassPlugin;
import dev.superseller.hourglass.config.HourGlassConfig;
import dev.superseller.hourglass.data.PlaytimeRecord;
import dev.superseller.hourglass.storage.YamlPlayerStorage;
import dev.superseller.hourglass.util.CsvBuilder;
import dev.superseller.hourglass.util.Dates;
import dev.superseller.hourglass.util.TimeFormat;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * The in-memory cache of every tracked player: lookups by UUID or name, record
 * creation, persistence, purging and CSV export.
 *
 * <p>Every playtime question in the plugin is answered from this cache — not
 * from disk — so commands, GUIs and placeholders cost nothing and Folia never
 * sees blocking I/O on a region thread.
 */
public final class PlaytimeService {

    /** What an export produced: where it went and how many rows it holds. */
    public record ExportResult(java.nio.file.Path file, int rows) {
    }

    private final HourGlassPlugin plugin;
    private final YamlPlayerStorage storage;
    private final Map<UUID, PlaytimeRecord> records = new ConcurrentHashMap<>();
    private final Map<String, UUID> byName = new ConcurrentHashMap<>();
    private volatile boolean loaded;

    public PlaytimeService(HourGlassPlugin plugin, YamlPlayerStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public YamlPlayerStorage storage() {
        return storage;
    }

    /** {@code true} once the startup load has finished. */
    public boolean loaded() {
        return loaded;
    }

    public void markLoaded() {
        this.loaded = true;
    }

    public int size() {
        return records.size();
    }

    public Collection<PlaytimeRecord> all() {
        return records.values();
    }

    public PlaytimeRecord of(UUID id) {
        return id == null ? null : records.get(id);
    }

    /** Fetches a record, creating a fresh one for unknown players. */
    public PlaytimeRecord getOrCreate(UUID id, String name) {
        PlaytimeRecord existing = records.get(id);
        if (existing != null) {
            return existing;
        }
        PlaytimeRecord created = new PlaytimeRecord(id, name);
        created.historySize(plugin.config().historySize());
        PlaytimeRecord raced = records.putIfAbsent(id, created);
        if (raced != null) {
            return raced;
        }
        rememberName(created);
        created.markDirty();
        return created;
    }

    /**
     * Inserts a record loaded from disk (or a test fixture).
     *
     * <p>If the player was already in the cache — they joined while the startup
     * load was still running and got an empty record — the stored values are
     * merged into the live record instead of replacing it, so the open session,
     * the idle timestamp and the seconds measured so far survive.
     */
    public void adopt(PlaytimeRecord record) {
        if (record == null) {
            return;
        }
        record.historySize(plugin.config().historySize());
        PlaytimeRecord existing = records.putIfAbsent(record.id(), record);
        if (existing == null) {
            rememberName(record);
            return;
        }
        existing.loadFrom(record.totalSeconds(), record.activeSeconds(), record.firstJoin(),
                record.lastSeen(), record.history(), record.awardedList(), record.frozen(), record.display());
        existing.name(record.name());
        if (existing.online()) {
            existing.rebaselineSession();
            existing.markDirty();
        }
        rememberName(existing);
    }

    private void rememberName(PlaytimeRecord record) {
        if (record.name() != null && !record.name().isBlank()) {
            byName.put(record.name().toLowerCase(Locale.US), record.id());
        }
    }

    /** Keeps the name index and the stored file in sync with a rename. */
    public void refreshName(PlaytimeRecord record, String name) {
        if (record == null || name == null || name.isBlank()) {
            return;
        }
        String previous = record.name();
        if (previous != null && !previous.isBlank()) {
            byName.remove(previous.toLowerCase(Locale.US), record.id());
        }
        record.name(name);
        rememberName(record);
    }

    /** Finds a record by player name without touching the network. */
    public PlaytimeRecord byName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        UUID id = byName.get(name.trim().toLowerCase(Locale.US));
        return id == null ? null : records.get(id);
    }

    /**
     * Resolves a name to a record, falling back to the server's own name cache
     * so that players who joined before this plugin was installed still work.
     */
    public PlaytimeRecord resolve(String name) {
        PlaytimeRecord direct = byName(name);
        if (direct != null) {
            return direct;
        }
        Player online = plugin.getServer().getPlayer(name);
        if (online != null) {
            return getOrCreate(online.getUniqueId(), online.getName());
        }
        try {
            OfflinePlayer offline = plugin.getServer().getOfflinePlayer(name);
            if (offline != null && offline.getUniqueId() != null) {
                PlaytimeRecord existing = records.get(offline.getUniqueId());
                if (existing != null) {
                    return existing;
                }
                if (offline.hasPlayedBefore()) {
                    return getOrCreate(offline.getUniqueId(), offline.getName());
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Name lookup for '" + name + "' failed: " + e.getMessage());
        }
        return null;
    }

    /** The seconds a metric name refers to, including the live session values. */
    public long metric(PlaytimeRecord record, String metric, long nowMillis) {
        if (record == null) {
            return 0L;
        }
        String resolved = plugin.config().resolveMetric(metric);
        return switch (resolved) {
            case "active" -> record.activeSeconds();
            case "session" -> record.sessionTotalSeconds(nowMillis);
            case "session-active" -> record.sessionActiveSeconds();
            default -> record.totalSeconds();
        };
    }

    /** The metric configured as "primary" — what {@code /playtime} reports. */
    public long primary(PlaytimeRecord record, long nowMillis) {
        return metric(record, "primary", nowMillis);
    }

    /** Renders a duration with the configured format. */
    public String pretty(long seconds) {
        return plugin.config().timeFormat().format(seconds);
    }

    /** Renders a duration with at most {@code max} units. */
    public String pretty(long seconds, int max) {
        return plugin.config().timeFormat().format(seconds, max);
    }

    public Dates dates() {
        return new Dates(plugin.config().dateFormat(), plugin.config().zone());
    }

    /** Queues the record for the next writer flush. */
    public void persist(PlaytimeRecord record) {
        storage.save(record);
    }

    /** Writes the record before the command returns (admin edits). */
    public void persistNow(PlaytimeRecord record) {
        storage.saveNow(record);
    }

    public int flushDirty() {
        return storage.saveAll(dirty());
    }

    public List<PlaytimeRecord> dirty() {
        List<PlaytimeRecord> out = new ArrayList<>();
        for (PlaytimeRecord record : records.values()) {
            if (record.dirty()) {
                out.add(record);
            }
        }
        return out;
    }

    /** Every online player's record, for the GUI and admin listings. */
    public List<PlaytimeRecord> online() {
        List<PlaytimeRecord> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlaytimeRecord record = records.get(player.getUniqueId());
            if (record != null) {
                out.add(record);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ purge

    /**
     * Drops players whose last seen timestamp is older than {@code cutoffMillis}.
     *
     * @param dryRun when {@code true} nothing is deleted, only counted
     * @return the affected players (deleted, or about to be deleted)
     */
    public List<PlaytimeRecord> purgeInactive(long cutoffMillis, boolean dryRun) {
        List<PlaytimeRecord> affected = new ArrayList<>();
        for (PlaytimeRecord record : new ArrayList<>(records.values())) {
            if (record.online()) {
                continue;
            }
            long seen = record.lastSeen();
            if (seen <= 0L || seen >= cutoffMillis) {
                continue;
            }
            affected.add(record);
            if (dryRun) {
                continue;
            }
            records.remove(record.id());
            String name = record.name();
            if (name != null) {
                byName.remove(name.toLowerCase(Locale.US), record.id());
            }
            storage.delete(record.id());
        }
        return affected;
    }

    /** Removes one player entirely (file + cache). */
    public boolean forget(PlaytimeRecord record) {
        if (record == null) {
            return false;
        }
        records.remove(record.id());
        String name = record.name();
        if (name != null) {
            byName.remove(name.toLowerCase(Locale.US), record.id());
        }
        return storage.delete(record.id());
    }

    // ----------------------------------------------------------------- export

    /**
     * Writes a CSV of every tracked player into {@code export.directory} and
     * returns the file. Sorted according to {@code export.sort}.
     */
    public ExportResult exportCsv() throws IOException {
        HourGlassConfig config = plugin.config();
        List<PlaytimeRecord> rows = new ArrayList<>(records.values());
        Comparator<PlaytimeRecord> comparator = switch (config.exportSort()) {
            case "active" -> Comparator.comparingLong(PlaytimeRecord::activeSeconds).reversed();
            case "name" -> Comparator.comparing(record -> record.name() == null ? "" : record.name(),
                    String.CASE_INSENSITIVE_ORDER);
            case "last-seen" -> Comparator.comparingLong(PlaytimeRecord::lastSeen).reversed();
            default -> Comparator.comparingLong(PlaytimeRecord::totalSeconds).reversed();
        };
        rows.sort(comparator);
        if (!config.exportIncludeOffline()) {
            rows.removeIf(record -> !record.online());
        }

        CsvBuilder csv = new CsvBuilder(config.exportNewline(), config.exportBom());
        csv.row("uuid", "name", "total_seconds", "total_readable", "active_seconds", "active_readable",
                "first_join", "last_seen", "online", "frozen", "sessions", "milestones");
        Dates dates = dates();
        TimeFormat format = config.timeFormat();
        for (PlaytimeRecord record : rows) {
            csv.row(record.id().toString(), record.name(),
                    record.totalSeconds(), format.format(record.totalSeconds()),
                    record.activeSeconds(), format.format(record.activeSeconds()),
                    dates.format(record.firstJoin(), ""), dates.format(record.lastSeen(), ""),
                    record.online() ? "yes" : "no",
                    record.frozen() ? "yes" : "no",
                    record.historyCount(),
                    String.join(" ", record.awardedList()));
        }
        Path dir = plugin.getDataFolder().toPath().resolve(config.exportDirectory());
        Files.createDirectories(dir);
        String stamp = LocalDate.now(config.zone()) + "-" + System.currentTimeMillis();
        Path file = dir.resolve("playtime-" + stamp.replace(':', '-') + ".csv");
        Files.writeString(file, csv.build(), java.nio.charset.StandardCharsets.UTF_8);
        return new ExportResult(file, rows.size());
    }

    /** Placeholder-friendly summary of the cache state. */
    public Map<String, String> storagePlaceholders() {
        long lastWrite = storage.lastWriteMillis();
        return Map.of(
                "players", String.valueOf(records.size()),
                "online", String.valueOf(online().size()),
                "queued_writes", String.valueOf(storage.queued()),
                "writes", String.valueOf(storage.writes()),
                "last_save", lastWrite <= 0L ? "never" : dates().format(lastWrite, "never"),
                "dirty", String.valueOf(dirty().size()));
    }

    /** Names of every tracked player, for tab completion. */
    public List<String> knownNames(int limit) {
        List<String> names = new ArrayList<>();
        for (PlaytimeRecord record : records.values()) {
            String name = record.name();
            if (name != null && !name.isBlank()) {
                names.add(name);
                if (limit > 0 && names.size() >= limit) {
                    break;
                }
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }
}
