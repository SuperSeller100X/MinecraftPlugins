package dev.superseller.hourglass.storage;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import dev.superseller.hourglass.config.YamlIO;
import dev.superseller.hourglass.data.PlaytimeRecord;
import dev.superseller.hourglass.data.Session;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * YAML persistence: one small file per player under {@code players/}.
 *
 * <h2>Why this shape</h2>
 * <ul>
 *   <li><b>One file per player</b> — a restart never rewrites a megabyte-wide
 *       flat file, and two servers can never corrupt each other's edits. Players
 *       stay human-readable and hand-editable.</li>
 *   <li><b>Dedicated writer thread</b> — blocking disk I/O never happens on the
 *       main or region thread, which matters most on Folia where there is no
 *       Bukkit async scheduler to hide behind.</li>
 *   <li><b>Coalescing</b> — repeated changes to the same player collapse into a
 *       single write, so an admin spamming {@code /playtimeadmin add} produces one
 *       file write, not twenty.</li>
 *   <li><b>Atomic replace</b> — see {@link YamlIO}: UTF-8 always, temp file then
 *       move, so a crash mid-save cannot truncate a record.</li>
 * </ul>
 *
 * <p>File names are the lower-case UUID with dashes, which is legal on NTFS,
 * ext4 and APFS alike and immune to case-insensitive lookups.
 */
public final class YamlPlayerStorage {

    /** Bumped if the on-disk layout ever changes, so loaders can migrate. */
    public static final int SCHEMA = 1;

    /** Outcome of a startup load. */
    public record LoadResult(int loaded, int failed, long millis, List<String> problems) {

        public String describe() {
            StringBuilder sb = new StringBuilder(loaded).append(" player file(s) read, ")
                    .append(failed).append(" failed in ").append(millis).append(" ms");
            if (!problems.isEmpty()) {
                sb.append(" (").append(String.join("; ", problems)).append(')');
            }
            return sb.toString();
        }
    }

    private final JavaPlugin plugin;
    private final Path directory;
    private final ExecutorService writer;
    private final Map<UUID, PlaytimeRecord> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean drainScheduled = new AtomicBoolean(false);
    private final AtomicLong writes = new AtomicLong();
    private final AtomicLong lastWriteMillis = new AtomicLong();
    private volatile boolean shuttingDown;

    public YamlPlayerStorage(JavaPlugin plugin, String subDirectory) {
        this.plugin = plugin;
        this.directory = plugin.getDataFolder().toPath().resolve(subDirectory);
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "HourGlass-IO");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not create " + directory + ": " + e.getMessage());
        }
    }

    public Path directory() {
        return directory;
    }

    public Path fileFor(UUID id) {
        return directory.resolve(id.toString() + ".yml");
    }

    public long writes() {
        return writes.get();
    }

    public long lastWriteMillis() {
        return lastWriteMillis.get();
    }

    public int queued() {
        return pending.size();
    }

    // ------------------------------------------------------------------- load

    /**
     * Reads every player file on the writer thread. {@code sink} receives each
     * record on that thread, so it must only touch concurrent structures.
     */
    public java.util.concurrent.CompletableFuture<LoadResult> loadAll(int limit,
            java.util.function.Consumer<PlaytimeRecord> sink) {
        final java.util.function.Consumer<PlaytimeRecord> consumer =
                sink == null ? record -> { } : sink;
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            List<String> problems = new ArrayList<>();
            int loaded = 0;
            int failed = 0;
            long started = System.currentTimeMillis();
            if (!Files.isDirectory(directory)) {
                return new LoadResult(0, 0, 0L, List.of());
            }
            try (Stream<Path> files = Files.list(directory)) {
                List<Path> yml = new ArrayList<>();
                files.filter(path -> {
                    String name = path.getFileName().toString();
                    return name.endsWith(".yml") && !name.endsWith(".tmp.yml");
                }).sorted().forEach(yml::add);
                for (Path file : yml) {
                    if (limit > 0 && loaded >= limit) {
                        problems.add("stopped at storage.max-players=" + limit);
                        break;
                    }
                    try {
                        UUID id = parseName(file.getFileName().toString());
                        if (id == null) {
                            failed++;
                            problems.add("unparsable file name " + file.getFileName());
                            continue;
                        }
                        PlaytimeRecord record = new PlaytimeRecord(id, null);
                        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                            apply(record, YamlConfiguration.loadConfiguration(reader));
                        }
                        loaded++;
                        consumer.accept(record);
                    } catch (IOException | RuntimeException e) {
                        failed++;
                        if (problems.size() < 8) {
                            problems.add(file.getFileName() + ": " + e.getClass().getSimpleName());
                        }
                    }
                }
            } catch (IOException e) {
                problems.add("directory unreadable: " + e.getMessage());
            }
            return new LoadResult(loaded, failed, System.currentTimeMillis() - started, problems);
        }, writer);
    }

    /** Parses {@code 0cb6...-....yml} back into a UUID, {@code null} if it is not one. */
    public static UUID parseName(String fileName) {
        if (fileName == null) {
            return null;
        }
        String raw = fileName.trim();
        if (raw.endsWith(".yml")) {
            raw = raw.substring(0, raw.length() - 4);
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------- save

    /** Queues a coalesced write for the record. Never blocks. */
    public void save(PlaytimeRecord record) {
        if (record == null || shuttingDown) {
            return;
        }
        pending.put(record.id(), record);
        scheduleDrain();
    }

    /** Queues writes for every dirty record and returns how many were queued. */
    public int saveAll(Collection<PlaytimeRecord> records) {
        int queuedCount = 0;
        for (PlaytimeRecord record : records) {
            if (record != null && record.dirty()) {
                pending.put(record.id(), record);
                queuedCount++;
            }
        }
        if (queuedCount > 0) {
            scheduleDrain();
        }
        return queuedCount;
    }

    private void scheduleDrain() {
        if (drainScheduled.compareAndSet(false, true)) {
            try {
                writer.execute(this::drain);
            } catch (RuntimeException e) {
                drainScheduled.set(false);
                plugin.getLogger().warning("Storage writer is shut down, saving inline: " + e.getMessage());
                drain();
            }
        }
    }

    private void drain() {
        try {
            List<PlaytimeRecord> batch = new ArrayList<>(pending.size());
            for (UUID id : List.copyOf(pending.keySet())) {
                PlaytimeRecord record = pending.remove(id);
                if (record != null) {
                    batch.add(record);
                }
            }
            for (PlaytimeRecord record : batch) {
                write(record);
            }
        } finally {
            drainScheduled.set(false);
            if (!pending.isEmpty() && !shuttingDown) {
                scheduleDrain();
            }
        }
    }

    private void write(PlaytimeRecord record) {
        try {
            YamlIO.writeAtomically(fileFor(record.id()), toYaml(record).saveToString());
            record.markClean();
            writes.incrementAndGet();
            lastWriteMillis.set(System.currentTimeMillis());
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().severe("Could not save playtime for " + record.name() + " ("
                    + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
        }
    }

    /**
     * Writes immediately on the calling thread. Used by {@code onDisable}, where
     * the writer thread may already be gone, and by admin edits that must be
     * durable before the command returns.
     */
    public boolean saveNow(PlaytimeRecord record) {
        if (record == null) {
            return false;
        }
        pending.remove(record.id());
        write(record);
        return true;
    }

    /** Flushes everything dirty right now, on the calling thread. */
    public int flushNow(Collection<PlaytimeRecord> records) {
        int saved = 0;
        for (PlaytimeRecord record : records) {
            if (record != null && record.dirty()) {
                saveNow(record);
                saved++;
            }
        }
        return saved;
    }

    /** Stops accepting work, flushes what is queued and joins the writer. */
    public void shutdown(long awaitSeconds) {
        shuttingDown = true;
        drain();
        writer.shutdown();
        try {
            if (!writer.awaitTermination(Math.max(1L, awaitSeconds), TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Storage writer did not finish within " + awaitSeconds
                        + "s; " + pending.size() + " record(s) may not be on disk.");
                writer.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }

    /** Removes a player's file (used by purge). Returns {@code true} if a file went away. */
    public boolean delete(UUID id) {
        pending.remove(id);
        try {
            return Files.deleteIfExists(fileFor(id));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not delete file for " + id + ": " + e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------ serialising

    /** Turns a record into the YAML document stored in its file. */
    public static YamlConfiguration toYaml(PlaytimeRecord record) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", SCHEMA);
        yaml.set("name", record.name());
        yaml.set("first-join", record.firstJoin());
        yaml.set("last-seen", record.lastSeen());
        yaml.set("total-seconds", record.totalSeconds());
        yaml.set("active-seconds", record.activeSeconds());
        yaml.set("frozen", record.frozen());
        yaml.set("display", record.display().name().toLowerCase(java.util.Locale.US));
        List<String> awarded = record.awardedList();
        if (!awarded.isEmpty()) {
            yaml.set("milestones", awarded);
        }
        List<Session> history = record.history();
        if (!history.isEmpty()) {
            List<Map<String, Object>> rows = new ArrayList<>(history.size());
            for (Session session : history) {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("start", session.start());
                row.put("end", session.end());
                row.put("total-seconds", session.totalSeconds());
                row.put("active-seconds", session.activeSeconds());
                rows.add(row);
            }
            yaml.set("history", rows);
        }
        return yaml;
    }

    /** Reads a stored document back into a record. Unknown keys are ignored. */
    public static void apply(PlaytimeRecord record, YamlConfiguration yaml) {
        long total = Math.max(0L, yaml.getLong("total-seconds", 0L));
        long active = Math.max(0L, yaml.getLong("active-seconds", 0L));
        List<Session> history = new ArrayList<>();
        for (Map<?, ?> row : yaml.getMapList("history")) {
            long start = number(row.get("start"));
            long end = number(row.get("end"));
            long sessionTotal = number(row.getOrDefault("total-seconds", 0L));
            long sessionActive = number(row.getOrDefault("active-seconds", 0L));
            history.add(new Session(start, end, Math.max(0L, sessionTotal), Math.max(0L, sessionActive)));
        }
        List<String> milestones = new ArrayList<>();
        Object raw = yaml.get("milestones");
        if (raw instanceof List<?> list) {
            for (Object element : list) {
                if (element != null) {
                    milestones.add(String.valueOf(element));
                }
            }
        }
        record.loadFrom(total, active,
                Math.max(0L, yaml.getLong("first-join", 0L)),
                Math.max(0L, yaml.getLong("last-seen", 0L)),
                history, milestones,
                yaml.getBoolean("frozen", false),
                PlaytimeRecord.DisplayMode.parse(yaml.getString("display", "inherit")));
        record.name(yaml.getString("name", null));
    }

    private static long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
