package dev.superseller.veltryx.spawner;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Write-through spawner records. Each purchased identity keeps its own 20
 * pages. A stack is a tree of records; attaching one root to another changes
 * only the source's parent field in one atomic file replacement. This keeps
 * all identities and previously stored loot through stack merges and restarts.
 */
public final class SpawnerStore {
    public static final int PAGES = 20, PAGE_SIZE = 45, CAPACITY = PAGES * PAGE_SIZE;
    public static final int MAX_STACK = 1024;
    public enum Mode { SPAWNER, INVENTORY; public Mode other() { return this == SPAWNER ? INVENTORY : SPAWNER; } }
    public record Position(UUID world, int x, int y, int z) {
        public static Position of(Block block) { return new Position(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()); }
    }
    public record Page(SpawnerType type, Mode mode, int stackSize, int totalPages, ItemStack[] items) { }
    public enum Deposit { STORED, FULL, CHANGED_MODE, INVALID, ERROR }
    public enum Merge { OK, DIFFERENT_TYPE, FULL, INVALID, ERROR }
    public enum WithdrawStatus { OK, FULL, STALE, INVALID, ERROR }
    public record Withdrawal(WithdrawStatus status, int amount) { }

    private static final class Record {
        final UUID id;
        final SpawnerType type;
        Mode mode = Mode.SPAWNER;
        Position position;
        UUID parent;
        ItemStack[] slots = new ItemStack[CAPACITY];
        Record(UUID id, SpawnerType type) { this.id = id; this.type = type; }
    }
    private final Path directory;
    private final Logger logger;
    private final Map<UUID, Record> records = new ConcurrentHashMap<>();
    /** Immutable snapshots, root first, all remaining identities sorted. */
    private final Map<UUID, List<Record>> groups = new ConcurrentHashMap<>();
    private final Set<UUID> reported = ConcurrentHashMap.newKeySet();
    private final Object stackLock = new Object();
    private volatile boolean indexHealthy = true;

    public SpawnerStore(Path directory, Logger logger) {
        this.directory = directory; this.logger = logger;
        loadIndex();
    }

    /** Reconstruct stack membership from parent links, refusing corrupt graphs. */
    private void loadIndex() {
        if (!Files.exists(directory)) return;
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".properties")).toList()) {
                String name = file.getFileName().toString();
                UUID id;
                try { id = UUID.fromString(name.substring(0, name.length() - ".properties".length())); }
                catch (IllegalArgumentException ex) { continue; }
                if (record(id) == null) throw new IllegalStateException("Unreadable spawner record " + id);
            }
            Map<UUID, List<Record>> rebuilt = new HashMap<>();
            for (Record member : records.values()) {
                Record current = member;
                Set<UUID> seen = new HashSet<>();
                while (current.parent != null) {
                    if (!seen.add(current.id) || current.position != null) throw new IllegalStateException("Invalid spawner parent chain " + member.id);
                    Record parent = records.get(current.parent);
                    if (parent == null || parent.type != member.type) throw new IllegalStateException("Missing or wrong-type parent for " + member.id);
                    current = parent;
                }
                rebuilt.computeIfAbsent(current.id, ignored -> new ArrayList<>()).add(member);
            }
            for (var entry : rebuilt.entrySet()) {
                if (entry.getValue().size() > MAX_STACK) throw new IllegalStateException("Oversized spawner stack " + entry.getKey());
                groups.put(entry.getKey(), ordered(entry.getKey(), entry.getValue()));
            }
        } catch (IOException | RuntimeException ex) {
            indexHealthy = false; groups.clear();
            logger.log(Level.SEVERE, "Could not reconstruct spawner stacks; custom spawners are disabled until repaired", ex);
        }
    }
    private static List<Record> ordered(UUID root, Collection<Record> records) {
        List<Record> result = new ArrayList<>(records);
        result.sort(Comparator.comparing((Record r) -> !r.id.equals(root)).thenComparing(r -> r.id.toString()));
        return List.copyOf(result);
    }

    public boolean create(SpawnerItems.Identity identity) {
        synchronized (stackLock) {
            if (!indexHealthy) return false;
            Record record = new Record(identity.id(), identity.type());
            synchronized (record) {
                if (Files.exists(path(identity.id())) || records.putIfAbsent(identity.id(), record) != null) return false;
                if (!write(record, record.mode, null, null, record.slots)) { records.remove(identity.id(), record); return false; }
                groups.put(identity.id(), List.of(record));
                return true;
            }
        }
    }
    public void discardUnplaced(SpawnerItems.Identity identity) {
        synchronized (stackLock) {
            Record record = record(identity.id());
            if (record == null) return;
            synchronized (record) {
                List<Record> group = groups.get(identity.id());
                if (record.type != identity.type() || record.position != null || record.parent != null || group == null || group.size() != 1) return;
                try { Files.deleteIfExists(path(identity.id())); records.remove(identity.id(), record); groups.remove(identity.id()); }
                catch (IOException e) { logger.log(Level.SEVERE, "Could not discard unused spawner " + identity.id(), e); }
            }
        }
    }
    /** A root identity can be placed only once; attached source items cannot be reused. */
    public boolean bind(SpawnerItems.Identity identity, Position position) {
        Record record = record(identity.id());
        if (record == null || position == null || !indexHealthy) return false;
        synchronized (record) {
            if (record.type != identity.type() || record.position != null || record.parent != null || !groups.containsKey(record.id)) return false;
            if (!write(record, record.mode, position, null, record.slots)) return false;
            record.position = position; return true;
        }
    }
    public boolean unbind(SpawnerItems.Identity identity, Position position) {
        Record record = record(identity.id());
        if (record == null || !indexHealthy) return false;
        synchronized (record) {
            if (!matches(record, identity, position)) return false;
            if (!write(record, record.mode, null, null, record.slots)) return false;
            record.position = null; return true;
        }
    }
    public Mode modeAt(SpawnerItems.Identity identity, Position position) {
        Record record = record(identity.id());
        if (record == null || !indexHealthy) return null;
        synchronized (record) { return matches(record, identity, position) ? record.mode : null; }
    }
    public int stackSize(SpawnerItems.Identity identity) {
        Record record = record(identity.id());
        if (record == null || !indexHealthy) return 0;
        synchronized (record) {
            List<Record> members = groups.get(record.id);
            return record.parent == null && record.type == identity.type() && members != null ? members.size() : 0;
        }
    }
    public int stackSizeAt(SpawnerItems.Identity identity, Position position) {
        Record record = record(identity.id());
        if (record == null || !indexHealthy) return 0;
        synchronized (record) {
            List<Record> members = groups.get(record.id);
            return matches(record, identity, position) && members != null ? members.size() : 0;
        }
    }

    /** The source is an unplaced root item. One atomic source write attaches its entire subtree. */
    public Merge merge(SpawnerItems.Identity target, Position position, SpawnerItems.Identity source) {
        if (source == null || target == null || source.id().equals(target.id()) || !indexHealthy) return Merge.INVALID;
        if (source.type() != target.type()) return Merge.DIFFERENT_TYPE;
        synchronized (stackLock) {
            Record root = record(target.id()), add = record(source.id());
            if (root == null || add == null) return Merge.INVALID;
            synchronized (root) { synchronized (add) {
                List<Record> left = groups.get(root.id), right = groups.get(add.id);
                if (!matches(root, target, position) || add.type != source.type() || add.parent != null
                        || add.position != null || left == null || right == null) return Merge.INVALID;
                if (left.size() + right.size() > MAX_STACK) return Merge.FULL;
                if (!write(add, add.mode, null, root.id, add.slots)) return Merge.ERROR;
                add.parent = root.id;
                List<Record> joined = new ArrayList<>(left); joined.addAll(right);
                groups.put(root.id, ordered(root.id, joined)); groups.remove(add.id);
                return Merge.OK;
            } }
        }
    }
    public Mode toggle(SpawnerItems.Identity identity, Position position) {
        Record record = record(identity.id());
        if (record == null || !indexHealthy) return null;
        synchronized (record) {
            if (!matches(record, identity, position)) return null;
            Mode next = record.mode.other();
            if (!write(record, next, record.position, null, record.slots)) return null;
            record.mode = next; return next;
        }
    }
    /** Joined storage exposes 20 pages per component, even for previously filled spawners. */
    public Page page(SpawnerItems.Identity identity, Position position, int page) {
        Record root = record(identity.id());
        if (root == null || !indexHealthy || page < 0) return null;
        synchronized (root) {
            List<Record> members = groups.get(root.id);
            if (!matches(root, identity, position) || members == null || page >= PAGES * members.size()) return null;
            Record member = members.get(page / PAGES);
            int offset = (page % PAGES) * PAGE_SIZE;
            ItemStack[] result = new ItemStack[PAGE_SIZE];
            for (int i = 0; i < PAGE_SIZE; i++) result[i] = copy(member.slots[offset + i]);
            return new Page(root.type, root.mode, members.size(), PAGES * members.size(), result);
        }
    }
    /**
     * Inserts all of a roll or batch, or none if the entire batch cannot fit.
     * Each affected member file is atomically replaced, preserving its old items
     * even if an I/O error interrupts a multi-member batch.
     */
    public Deposit deposit(SpawnerItems.Identity identity, Position position, Collection<ItemStack> loot) {
        Record root = record(identity.id());
        if (root == null || !indexHealthy) return Deposit.INVALID;
        synchronized (root) {
            List<Record> members = groups.get(root.id);
            if (!matches(root, identity, position) || members == null) return Deposit.INVALID;
            if (root.mode != Mode.INVENTORY) return Deposit.CHANGED_MODE;
            Map<Record, ItemStack[]> candidates = new java.util.LinkedHashMap<>();
            for (ItemStack item : loot) {
                if (empty(item)) continue;
                int remaining = item.getAmount();
                int max = Math.max(1, item.getMaxStackSize());
                for (Record member : members) {
                    ItemStack[] candidate = candidates.computeIfAbsent(member, r -> copy(r.slots));
                    for (ItemStack slot : candidate) {
                        if (!empty(slot) && slot.isSimilar(item) && slot.getAmount() < max) {
                            int add = Math.min(remaining, max - slot.getAmount());
                            slot.setAmount(slot.getAmount() + add); remaining -= add;
                            if (remaining == 0) break;
                        }
                    }
                    for (int i = 0; remaining > 0 && i < candidate.length; i++) {
                        if (empty(candidate[i])) {
                            int add = Math.min(remaining, max);
                            candidate[i] = item.clone(); candidate[i].setAmount(add); remaining -= add;
                        }
                    }
                    if (remaining == 0) break;
                }
                if (remaining > 0) return Deposit.FULL;
            }
            for (var entry : candidates.entrySet()) {
                Record member = entry.getKey();
                if (!write(member, member.mode, member.position, member.parent, entry.getValue())) return Deposit.ERROR;
                member.slots = entry.getValue();
            }
            return Deposit.STORED;
        }
    }
    public Withdrawal withdraw(SpawnerItems.Identity identity, Position position, int slot,
                               ItemStack displayed, boolean one, PlayerInventory inventory) {
        Record root = record(identity.id());
        if (root == null || !indexHealthy || slot < 0) return new Withdrawal(WithdrawStatus.INVALID, 0);
        synchronized (root) {
            List<Record> members = groups.get(root.id);
            if (!matches(root, identity, position) || members == null || slot >= CAPACITY * members.size()) return new Withdrawal(WithdrawStatus.INVALID, 0);
            int page = slot / PAGE_SIZE;
            Record member = members.get(page / PAGES);
            int localSlot = (page % PAGES) * PAGE_SIZE + slot % PAGE_SIZE;
            ItemStack stored = member.slots[localSlot];
            if (empty(stored) || empty(displayed) || !stored.isSimilar(displayed)) return new Withdrawal(WithdrawStatus.STALE, 0);
            int requested = one ? 1 : stored.getAmount();
            ItemStack delivery = stored.clone(); delivery.setAmount(requested);
            ItemStack[] before = copy(inventory.getStorageContents());
            int accepted;
            try {
                Map<Integer, ItemStack> leftovers = inventory.addItem(delivery);
                long remaining = 0;
                for (ItemStack item : leftovers.values()) if (!empty(item)) remaining += item.getAmount();
                accepted = (int) Math.max(0L, requested - remaining);
            } catch (RuntimeException e) {
                inventory.setStorageContents(before); logger.log(Level.SEVERE, "Could not deliver spawner loot for " + identity.id(), e);
                return new Withdrawal(WithdrawStatus.ERROR, 0);
            }
            if (accepted <= 0) return new Withdrawal(WithdrawStatus.FULL, 0);
            ItemStack[] candidate = copy(member.slots);
            int remaining = stored.getAmount() - accepted;
            if (remaining == 0) candidate[localSlot] = null; else candidate[localSlot].setAmount(remaining);
            if (!write(member, member.mode, member.position, member.parent, candidate)) {
                inventory.setStorageContents(before); return new Withdrawal(WithdrawStatus.ERROR, 0);
            }
            member.slots = candidate; return new Withdrawal(WithdrawStatus.OK, accepted);
        }
    }
    private boolean matches(Record record, SpawnerItems.Identity identity, Position position) {
        return record.parent == null && record.type == identity.type() && position != null && position.equals(record.position);
    }
    private Record record(UUID id) { return records.computeIfAbsent(id, this::read); }
    private Path path(UUID id) { return directory.resolve(id + ".properties"); }
    private Record read(UUID id) {
        Path file = path(id);
        if (!Files.exists(file)) {
            if (reported.add(id)) logger.severe("Missing custom spawner storage for " + id + "; spawner disabled.");
            return null;
        }
        Properties data = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            data.load(in);
            if (!"1".equals(data.getProperty("format"))) throw new IllegalArgumentException("unknown format");
            SpawnerType type = SpawnerType.parse(data.getProperty("type"));
            if (type == null) throw new IllegalArgumentException("unknown spawner type");
            Record record = new Record(id, type);
            record.mode = Mode.valueOf(data.getProperty("mode"));
            String world = data.getProperty("world", "");
            if (!world.isBlank()) record.position = new Position(UUID.fromString(world), Integer.parseInt(data.getProperty("x")),
                    Integer.parseInt(data.getProperty("y")), Integer.parseInt(data.getProperty("z")));
            String parent = data.getProperty("parent", "");
            if (!parent.isBlank()) record.parent = UUID.fromString(parent);
            for (String key : data.stringPropertyNames()) {
                if (!key.startsWith("slot.")) continue;
                int slot = Integer.parseInt(key.substring(5));
                if (slot < 0 || slot >= CAPACITY) throw new IllegalArgumentException("invalid storage slot " + slot);
                ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(data.getProperty(key)));
                if (empty(item)) throw new IllegalArgumentException("empty saved item in slot " + slot);
                record.slots[slot] = item;
            }
            reported.remove(id); return record;
        } catch (IOException | RuntimeException e) {
            if (reported.add(id)) logger.log(Level.SEVERE, "Could not read custom spawner " + id + "; spawner disabled", e);
            return null;
        }
    }
    private boolean write(Record record, Mode mode, Position position, UUID parent, ItemStack[] slots) {
        try {
            Properties data = new Properties();
            data.setProperty("format", "1"); data.setProperty("type", record.type.name()); data.setProperty("mode", mode.name());
            if (parent != null) data.setProperty("parent", parent.toString());
            if (position != null) {
                data.setProperty("world", position.world().toString()); data.setProperty("x", Integer.toString(position.x()));
                data.setProperty("y", Integer.toString(position.y())); data.setProperty("z", Integer.toString(position.z()));
            }
            for (int i = 0; i < slots.length; i++) if (!empty(slots[i]))
                data.setProperty("slot." + i, Base64.getEncoder().encodeToString(slots[i].serializeAsBytes()));
            Files.createDirectories(directory);
            Path file = path(record.id), temp = directory.resolve(record.id + ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) { data.store(out, "Veltryx custom spawner"); }
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException | RuntimeException e) {
            logger.log(Level.SEVERE, "Could not save custom spawner " + record.id, e); return false;
        }
    }
    private static boolean empty(ItemStack item) { return item == null || item.getType() == Material.AIR || item.getAmount() <= 0; }
    private static ItemStack copy(ItemStack item) { return empty(item) ? null : item.clone(); }
    private static ItemStack[] copy(ItemStack[] items) {
        ItemStack[] result = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) result[i] = copy(items[i]);
        return result;
    }
}
