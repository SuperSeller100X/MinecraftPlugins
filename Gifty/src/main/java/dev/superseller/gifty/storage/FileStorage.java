package dev.superseller.gifty.storage;

import dev.superseller.gifty.model.Delivery;
import dev.superseller.gifty.model.Inbox;
import dev.superseller.gifty.model.SerialItem;
import dev.superseller.gifty.util.MiniYaml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Flat-file storage: one YAML-subset file per inbox under data/inboxes/ and
 * a data/meta.yml file with the id sequence, counters and queued returns.
 * All methods are thread-safe (Folia region threads may call concurrently).
 */
public final class FileStorage {

    private final File dataDir;
    private final File inboxDir;
    private final Logger logger;

    private final Map<UUID, Inbox> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> dirty = new ConcurrentHashMap<>();

    private long nextId = 1L;
    private final Map<UUID, Long> sentCount = new ConcurrentHashMap<>();
    private final Map<UUID, Long> receivedCount = new ConcurrentHashMap<>();
    private final Map<UUID, List<SerialItem>> queuedReturns = new ConcurrentHashMap<>();
    private boolean metaLoaded = false;

    public FileStorage(File dataDir, Logger logger) {
        this.dataDir = dataDir;
        this.inboxDir = new File(dataDir, "inboxes");
        this.logger = logger;
        dataDir.mkdirs();
        inboxDir.mkdirs();
    }

    // ------------------------------------------------------------ inboxes

    public Inbox loadInbox(UUID owner) {
        Inbox cached = cache.get(owner);
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            cached = cache.get(owner);
            if (cached != null) {
                return cached;
            }
            Inbox inbox = new Inbox(owner);
            File file = inboxFile(owner);
            if (file.exists()) {
                try {
                    String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                    inbox = Inbox.fromMap(owner, MiniYaml.parse(text));
                } catch (Exception e) {
                    logger.warning("Could not load inbox for " + owner + ": " + e.getMessage());
                }
            }
            cache.put(owner, inbox);
            return inbox;
        }
    }

    public void saveInbox(UUID owner) {
        Inbox inbox = cache.get(owner);
        if (inbox == null) {
            return;
        }
        synchronized (this) {
            if (inbox.isEmpty()) {
                File file = inboxFile(owner);
                file.delete();
                dirty.remove(owner);
                return;
            }
            writeAtomic(inboxFile(owner), MiniYaml.dump(inbox.toMap()));
            dirty.remove(owner);
        }
    }

    public void saveAll() {
        for (UUID owner : cache.keySet()) {
            saveInbox(owner);
        }
        saveMeta();
    }

    public void markDirty(UUID owner) {
        dirty.put(owner, Boolean.TRUE);
    }

    public int pendingCount(UUID owner) {
        return loadInbox(owner).size();
    }

    // ------------------------------------------------------------ meta

    @SuppressWarnings("unchecked")
    private synchronized void ensureMeta() {
        if (metaLoaded) {
            return;
        }
        File file = metaFile();
        if (file.exists()) {
            try {
                String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                Map<String, Object> map = MiniYaml.parse(text);
                Object id = map.get("nextId");
                if (id instanceof Number) {
                    nextId = Math.max(1L, ((Number) id).longValue());
                }
                Object sent = map.get("sent");
                if (sent instanceof Map) {
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) sent).entrySet()) {
                        try {
                            sentCount.put(UUID.fromString(String.valueOf(e.getKey())), longVal(e.getValue()));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                Object received = map.get("received");
                if (received instanceof Map) {
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) received).entrySet()) {
                        try {
                            receivedCount.put(UUID.fromString(String.valueOf(e.getKey())), longVal(e.getValue()));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                Object returns = map.get("returns");
                if (returns instanceof Map) {
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) returns).entrySet()) {
                        try {
                            UUID uuid = UUID.fromString(String.valueOf(e.getKey()));
                            List<SerialItem> items = new ArrayList<>();
                            if (e.getValue() instanceof List) {
                                for (Object o : (List<Object>) e.getValue()) {
                                    if (o instanceof Map) {
                                        items.add(SerialItem.fromMap((Map<String, Object>) o));
                                    }
                                }
                            }
                            if (!items.isEmpty()) {
                                queuedReturns.put(uuid, items);
                            }
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Could not read meta.yml: " + e.getMessage());
            }
        }
        metaLoaded = true;
    }

    public synchronized long nextId() {
        ensureMeta();
        return nextId++;
    }

    public long totalSent(UUID player) {
        ensureMeta();
        Long v = sentCount.get(player);
        return v == null ? 0L : v;
    }

    public long totalReceived(UUID player) {
        ensureMeta();
        Long v = receivedCount.get(player);
        return v == null ? 0L : v;
    }

    public synchronized void bumpSent(UUID player, long count) {
        ensureMeta();
        sentCount.put(player, totalSentUnsafe(player) + count);
    }

    public synchronized void bumpReceived(UUID player, long count) {
        ensureMeta();
        receivedCount.put(player, totalReceivedUnsafe(player) + count);
    }

    private long totalSentUnsafe(UUID player) {
        Long v = sentCount.get(player);
        return v == null ? 0L : v;
    }

    private long totalReceivedUnsafe(UUID player) {
        Long v = receivedCount.get(player);
        return v == null ? 0L : v;
    }

    public synchronized void saveMeta() {
        ensureMeta();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("nextId", nextId);
        Map<String, Object> sent = new LinkedHashMap<>();
        for (Map.Entry<UUID, Long> e : sentCount.entrySet()) {
            sent.put(e.getKey().toString(), e.getValue());
        }
        Map<String, Object> received = new LinkedHashMap<>();
        for (Map.Entry<UUID, Long> e : receivedCount.entrySet()) {
            received.put(e.getKey().toString(), e.getValue());
        }
        Map<String, Object> returns = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<SerialItem>> e : queuedReturns.entrySet()) {
            List<Object> items = new ArrayList<>();
            for (SerialItem i : e.getValue()) {
                items.add(i.toMap());
            }
            returns.put(e.getKey().toString(), items);
        }
        map.put("sent", sent);
        map.put("received", received);
        map.put("returns", returns);
        writeAtomic(metaFile(), MiniYaml.dump(map));
    }

    // ------------------------------------------------------------ returns

    /** Queues items to be handed back to a player on their next join. */
    public synchronized void queueReturn(UUID player, List<SerialItem> items) {
        ensureMeta();
        List<SerialItem> existing = queuedReturns.get(player);
        if (existing == null) {
            existing = new ArrayList<>();
            queuedReturns.put(player, existing);
        }
        existing.addAll(items);
        saveMeta();
    }

    public synchronized List<SerialItem> takeReturns(UUID player) {
        ensureMeta();
        List<SerialItem> items = queuedReturns.remove(player);
        if (items == null) {
            items = new ArrayList<>();
        } else {
            saveMeta();
        }
        return items;
    }

    // ------------------------------------------------------------ helpers

    private File inboxFile(UUID owner) {
        return new File(inboxDir, owner + ".yml");
    }

    private File metaFile() {
        return new File(dataDir, "meta.yml");
    }

    private void writeAtomic(File target, String content) {
        try {
            File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
            Files.write(tmp.toPath(), content.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.warning("Could not write " + target.getName() + ": " + e.getMessage());
        }
    }

    private static long longVal(Object o) {
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        if (o != null) {
            try {
                return Long.parseLong(String.valueOf(o));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0L;
    }

    public void dropDeliveryForGc(UUID owner) {
        cache.remove(owner);
        dirty.remove(owner);
    }

    /** All inbox owner UUIDs currently loaded in memory. */
    public java.util.Set<UUID> cachedOwners() {
        return cache.keySet();
    }
}
