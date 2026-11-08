package dev.superseller.veltryx.spawner;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Write-through storage for each spawner. Records are keyed by the permanent
 * item ID, not its coordinates, so moving a spawner also moves its 20 pages.
 * Item bytes use Paper's NBT serializer to preserve loot-table metadata.
 * Separate record locks allow different Folia regions to update independently.
 */
public final class SpawnerStore {
    public static final int PAGES = 20;
    public static final int PAGE_SIZE = 45;
    public static final int CAPACITY = PAGES * PAGE_SIZE;

    public enum Mode { SPAWNER, INVENTORY;
        public Mode other() { return this == SPAWNER ? INVENTORY : SPAWNER; }
    }
    public record Position(UUID world, int x, int y, int z) {
        public static Position of(Block block) {
            return new Position(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
    public record Page(SpawnerType type, Mode mode, ItemStack[] items) { }
    public enum Deposit { STORED, FULL, CHANGED_MODE, INVALID, ERROR }
    public enum WithdrawStatus { OK, FULL, STALE, INVALID, ERROR }
    public record Withdrawal(WithdrawStatus status, int amount) { }

    private static final class Record {
        final UUID id;
        final SpawnerType type;
        Mode mode = Mode.SPAWNER;
        Position position;
        ItemStack[] slots = new ItemStack[CAPACITY];
        Record(UUID id, SpawnerType type) { this.id = id; this.type = type; }
    }

    private final Path directory;
    private final Logger logger;
    private final Map<UUID, Record> records = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> reported = ConcurrentHashMap.newKeySet();

    public SpawnerStore(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    /** Creates a new, unplaced identity; fails closed if it cannot be saved. */
    public boolean create(SpawnerItems.Identity identity) {
        Record record = new Record(identity.id(), identity.type());
        synchronized (record) {
            if (Files.exists(path(identity.id())) || records.putIfAbsent(identity.id(), record) != null) return false;
            if (!write(record, record.mode, null, record.slots)) {
                records.remove(identity.id(), record);
                return false;
            }
        }
        return true;
    }

    /** Roll back a purchase that could not deliver its newly created item. */
    public void discardUnplaced(SpawnerItems.Identity identity) {
        Record record = record(identity.id());
        if (record == null) return;
        synchronized (record) {
            if (record.type != identity.type() || record.position != null) return;
            try {
                Files.deleteIfExists(path(identity.id()));
                records.remove(identity.id(), record);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Could not discard unused spawner " + identity.id(), e);
            }
        }
    }

    /** One identity can be placed at only one location, even if an item is copied. */
    public boolean bind(SpawnerItems.Identity identity, Position position) {
        Record record = record(identity.id());
        if (record == null || position == null) return false;
        synchronized (record) {
            if (record.type != identity.type() || record.position != null) return false;
            if (!write(record, record.mode, position, record.slots)) return false;
            record.position = position;
            return true;
        }
    }

    public boolean unbind(SpawnerItems.Identity identity, Position position) {
        Record record = record(identity.id());
        if (record == null) return false;
        synchronized (record) {
            if (!matches(record, identity, position)) return false;
            if (!write(record, record.mode, null, record.slots)) return false;
            record.position = null;
            return true;
        }
    }

    public Mode modeAt(SpawnerItems.Identity identity, Position position) {
        Record record = record(identity.id());
        if (record == null) return null;
        synchronized (record) { return matches(record, identity, position) ? record.mode : null; }
    }

    public Mode toggle(SpawnerItems.Identity identity, Position position) {
        Record record = record(identity.id());
        if (record == null) return null;
        synchronized (record) {
            if (!matches(record, identity, position)) return null;
            Mode next = record.mode.other();
            if (!write(record, next, record.position, record.slots)) return null;
            record.mode = next;
            return next;
        }
    }

    /** Returns a detached view; no GUI is ever the authoritative inventory. */
    public Page page(SpawnerItems.Identity identity, Position position, int page) {
        Record record = record(identity.id());
        if (record == null || page < 0 || page >= PAGES) return null;
        synchronized (record) {
            if (!matches(record, identity, position)) return null;
            ItemStack[] items = new ItemStack[PAGE_SIZE];
            for (int i = 0; i < PAGE_SIZE; i++) items[i] = copy(record.slots[page * PAGE_SIZE + i]);
            return new Page(record.type, record.mode, items);
        }
    }

    /** Atomically inserts an entire loot-table roll, or leaves storage unchanged if it does not fit. */
    public Deposit deposit(SpawnerItems.Identity identity, Position position, Collection<ItemStack> loot) {
        Record record = record(identity.id());
        if (record == null) return Deposit.INVALID;
        synchronized (record) {
            if (!matches(record, identity, position)) return Deposit.INVALID;
            if (record.mode != Mode.INVENTORY) return Deposit.CHANGED_MODE;
            ItemStack[] candidate = copy(record.slots);
            boolean changed = false;
            for (ItemStack item : loot) {
                if (empty(item)) continue;
                changed = true;
                int remaining = item.getAmount();
                int max = Math.max(1, item.getMaxStackSize());
                for (ItemStack slot : candidate) {
                    if (!empty(slot) && slot.isSimilar(item) && slot.getAmount() < max) {
                        int add = Math.min(remaining, max - slot.getAmount());
                        slot.setAmount(slot.getAmount() + add);
                        remaining -= add;
                        if (remaining == 0) break;
                    }
                }
                for (int i = 0; remaining > 0 && i < candidate.length; i++) {
                    if (empty(candidate[i])) {
                        int add = Math.min(remaining, max);
                        candidate[i] = item.clone();
                        candidate[i].setAmount(add);
                        remaining -= add;
                    }
                }
                if (remaining > 0) return Deposit.FULL;
            }
            if (!changed) return Deposit.STORED;
            if (!write(record, record.mode, record.position, candidate)) return Deposit.ERROR;
            record.slots = candidate;
            return Deposit.STORED;
        }
    }

    /**
     * Takes items directly into the player's storage inventory. A stale GUI
     * cannot withdraw a replacement item, and disk failures roll back delivery.
     */
    public Withdrawal withdraw(SpawnerItems.Identity identity, Position position, int slot,
                               ItemStack displayed, boolean one, PlayerInventory inventory) {
        Record record = record(identity.id());
        if (record == null || slot < 0 || slot >= CAPACITY) return new Withdrawal(WithdrawStatus.INVALID, 0);
        synchronized (record) {
            if (!matches(record, identity, position)) return new Withdrawal(WithdrawStatus.INVALID, 0);
            ItemStack stored = record.slots[slot];
            if (empty(stored) || empty(displayed) || !stored.isSimilar(displayed)) {
                return new Withdrawal(WithdrawStatus.STALE, 0);
            }
            int requested = one ? 1 : stored.getAmount();
            ItemStack delivery = stored.clone();
            delivery.setAmount(requested);
            ItemStack[] before = copy(inventory.getStorageContents());
            int accepted;
            try {
                Map<Integer, ItemStack> leftovers = inventory.addItem(delivery);
                long remaining = 0;
                for (ItemStack item : leftovers.values()) if (!empty(item)) remaining += item.getAmount();
                accepted = (int) Math.max(0L, requested - remaining);
            } catch (RuntimeException e) {
                inventory.setStorageContents(before);
                logger.log(Level.SEVERE, "Could not deliver spawner loot for " + identity.id(), e);
                return new Withdrawal(WithdrawStatus.ERROR, 0);
            }
            if (accepted <= 0) return new Withdrawal(WithdrawStatus.FULL, 0);
            ItemStack[] candidate = copy(record.slots);
            int remaining = stored.getAmount() - accepted;
            if (remaining == 0) candidate[slot] = null;
            else candidate[slot].setAmount(remaining);
            if (!write(record, record.mode, record.position, candidate)) {
                inventory.setStorageContents(before);
                return new Withdrawal(WithdrawStatus.ERROR, 0);
            }
            record.slots = candidate;
            return new Withdrawal(WithdrawStatus.OK, accepted);
        }
    }

    private boolean matches(Record record, SpawnerItems.Identity identity, Position position) {
        return record.type == identity.type() && position != null && position.equals(record.position);
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
            if (!world.isBlank()) {
                record.position = new Position(UUID.fromString(world), Integer.parseInt(data.getProperty("x")),
                        Integer.parseInt(data.getProperty("y")), Integer.parseInt(data.getProperty("z")));
            }
            for (String key : data.stringPropertyNames()) {
                if (!key.startsWith("slot.")) continue;
                int slot = Integer.parseInt(key.substring(5));
                if (slot < 0 || slot >= CAPACITY) throw new IllegalArgumentException("invalid storage slot " + slot);
                ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(data.getProperty(key)));
                if (empty(item)) throw new IllegalArgumentException("empty saved item in slot " + slot);
                record.slots[slot] = item;
            }
            reported.remove(id);
            return record;
        } catch (IOException | RuntimeException e) {
            if (reported.add(id)) logger.log(Level.SEVERE, "Could not read custom spawner " + id + "; spawner disabled", e);
            return null;
        }
    }

    private boolean write(Record record, Mode mode, Position position, ItemStack[] slots) {
        try {
            Properties data = new Properties();
            data.setProperty("format", "1");
            data.setProperty("type", record.type.name());
            data.setProperty("mode", mode.name());
            if (position != null) {
                data.setProperty("world", position.world().toString());
                data.setProperty("x", Integer.toString(position.x()));
                data.setProperty("y", Integer.toString(position.y()));
                data.setProperty("z", Integer.toString(position.z()));
            }
            for (int i = 0; i < slots.length; i++) {
                if (!empty(slots[i])) data.setProperty("slot." + i, Base64.getEncoder().encodeToString(slots[i].serializeAsBytes()));
            }
            Files.createDirectories(directory);
            Path file = path(record.id);
            Path temp = directory.resolve(record.id + ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) { data.store(out, "Veltryx custom spawner"); }
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException | RuntimeException e) {
            logger.log(Level.SEVERE, "Could not save custom spawner " + record.id, e);
            return false;
        }
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }
    private static ItemStack copy(ItemStack item) { return empty(item) ? null : item.clone(); }
    private static ItemStack[] copy(ItemStack[] items) {
        ItemStack[] result = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) result[i] = copy(items[i]);
        return result;
    }
}
