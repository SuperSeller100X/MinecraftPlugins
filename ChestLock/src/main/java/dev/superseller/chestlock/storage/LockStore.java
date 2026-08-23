package dev.superseller.chestlock.storage;

import dev.superseller.chestlock.ChestLockPlugin;
import dev.superseller.chestlock.config.PluginConfig;
import dev.superseller.chestlock.model.LockData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Region-thread-only access to lock metadata stored directly on container TileStates. */
public final class LockStore {
    private static final int DATA_VERSION = 1;
    private final NamespacedKey versionKey;
    private final NamespacedKey lockIdKey;
    private final NamespacedKey ownerIdKey;
    private final NamespacedKey ownerNameKey;
    private final NamespacedKey saltKey;
    private final NamespacedKey hashKey;
    private final NamespacedKey iterationsKey;
    private final NamespacedKey durationKey;
    private final NamespacedKey keyTokenKey;
    private final NamespacedKey createdAtKey;
    private final NamespacedKey itemLockIdKey;
    private final NamespacedKey itemKeyTokenKey;

    public LockStore(ChestLockPlugin plugin) {
        versionKey = new NamespacedKey(plugin, "data_version");
        lockIdKey = new NamespacedKey(plugin, "lock_id");
        ownerIdKey = new NamespacedKey(plugin, "owner_id");
        ownerNameKey = new NamespacedKey(plugin, "owner_name");
        saltKey = new NamespacedKey(plugin, "salt");
        hashKey = new NamespacedKey(plugin, "hash");
        iterationsKey = new NamespacedKey(plugin, "iterations");
        durationKey = new NamespacedKey(plugin, "unlock_duration_ms");
        keyTokenKey = new NamespacedKey(plugin, "key_token");
        createdAtKey = new NamespacedKey(plugin, "created_at");
        itemLockIdKey = new NamespacedKey(plugin, "key_lock_id");
        itemKeyTokenKey = new NamespacedKey(plugin, "key_token");
    }

    public boolean isContainer(Material material) {
        return material == Material.CHEST
                || material == Material.TRAPPED_CHEST
                || material == Material.BARREL
                || material == Material.SHULKER_BOX
                || material.name().endsWith("_SHULKER_BOX");
    }

    public boolean canCreateLock(Material material, PluginConfig config) {
        if (material == Material.CHEST || material == Material.TRAPPED_CHEST) {
            return config.lockChests();
        }
        if (material == Material.BARREL) {
            return config.lockBarrels();
        }
        if (material == Material.SHULKER_BOX || material.name().endsWith("_SHULKER_BOX")) {
            return config.lockShulkerBoxes();
        }
        return false;
    }

    public ContainerGroup resolve(Block block) {
        if (!isContainer(block.getType()) || !(block.getState() instanceof Container container)) {
            return null;
        }
        List<Block> blocks = new ArrayList<>();
        Inventory inventory = container.getInventory();
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof DoubleChest doubleChest) {
            addChestSide(blocks, doubleChest.getLeftSide());
            addChestSide(blocks, doubleChest.getRightSide());
        }
        if (blocks.isEmpty()) {
            blocks.add(block);
        }
        blocks.sort(Comparator.comparingInt(Block::getX)
                .thenComparingInt(Block::getY)
                .thenComparingInt(Block::getZ));
        return new ContainerGroup(List.copyOf(blocks), inventory);
    }

    private static void addChestSide(List<Block> blocks, InventoryHolder holder) {
        if (holder instanceof org.bukkit.block.Chest chest) {
            blocks.add(chest.getBlock());
        }
    }

    public Lookup lookup(Block block) {
        ContainerGroup group = resolve(block);
        if (group == null) {
            return Lookup.unsupported();
        }
        LockData found = null;
        boolean corrupt = false;
        for (Block member : group.blocks()) {
            if (!(member.getState() instanceof Container state)) {
                corrupt = true;
                continue;
            }
            ReadResult read = read(state.getPersistentDataContainer());
            if (read.corrupt()) {
                corrupt = true;
            }
            if (read.data() != null) {
                if (found != null && !found.samePersistedData(read.data())) {
                    corrupt = true;
                } else {
                    found = read.data();
                }
            }
        }
        return new Lookup(group, found, corrupt);
    }

    public boolean create(ContainerGroup group, LockData data) {
        for (Block block : group.blocks()) {
            if (!(block.getState() instanceof Container container)
                    || read(container.getPersistentDataContainer()).secured()) {
                return false;
            }
        }
        return write(group, data);
    }

    public boolean update(ContainerGroup group, LockData data) {
        return write(group, data);
    }

    private boolean write(ContainerGroup group, LockData data) {
        boolean success = true;
        for (Block block : group.blocks()) {
            if (!(block.getState() instanceof Container container)) {
                success = false;
                continue;
            }
            PersistentDataContainer pdc = container.getPersistentDataContainer();
            pdc.set(versionKey, PersistentDataType.INTEGER, DATA_VERSION);
            pdc.set(lockIdKey, PersistentDataType.STRING, data.lockId().toString());
            pdc.set(ownerIdKey, PersistentDataType.STRING, data.ownerId().toString());
            pdc.set(ownerNameKey, PersistentDataType.STRING, data.ownerName());
            pdc.set(saltKey, PersistentDataType.BYTE_ARRAY, data.salt());
            pdc.set(hashKey, PersistentDataType.BYTE_ARRAY, data.hash());
            pdc.set(iterationsKey, PersistentDataType.INTEGER, data.iterations());
            pdc.set(durationKey, PersistentDataType.LONG, data.unlockDurationMillis());
            pdc.set(keyTokenKey, PersistentDataType.STRING, data.keyToken().toString());
            pdc.set(createdAtKey, PersistentDataType.LONG, data.createdAtMillis());
            success &= container.update(true, false);
        }
        return success;
    }

    public boolean clear(ContainerGroup group) {
        boolean success = true;
        for (Block block : group.blocks()) {
            if (!(block.getState() instanceof Container container)) {
                success = false;
                continue;
            }
            removeLockMetadata(container.getPersistentDataContainer());
            success &= container.update(true, false);
        }
        return success;
    }

    /**
     * Removes copied lock metadata from only this newly placed block. This prevents a shulker-box
     * item or creative clone from duplicating a lock UUID while leaving an adjacent locked chest untouched.
     */
    public boolean clearCopiedMetadata(Block block) {
        if (!(block.getState() instanceof Container container)
                || !read(container.getPersistentDataContainer()).secured()) {
            return false;
        }
        removeLockMetadata(container.getPersistentDataContainer());
        container.update(true, false);
        return true;
    }

    /** Removes lock metadata embedded in a dropped BlockState item, most notably a shulker box. */
    public boolean clearCopiedMetadata(ItemStack item) {
        if (!(item.getItemMeta() instanceof BlockStateMeta meta)) {
            return false;
        }
        BlockState state = meta.getBlockState();
        if (!(state instanceof Container container)
                || !read(container.getPersistentDataContainer()).secured()) {
            return false;
        }
        removeLockMetadata(container.getPersistentDataContainer());
        meta.setBlockState(container);
        item.setItemMeta(meta);
        return true;
    }

    private void removeLockMetadata(PersistentDataContainer pdc) {
        pdc.remove(versionKey);
        pdc.remove(lockIdKey);
        pdc.remove(ownerIdKey);
        pdc.remove(ownerNameKey);
        pdc.remove(saltKey);
        pdc.remove(hashKey);
        pdc.remove(iterationsKey);
        pdc.remove(durationKey);
        pdc.remove(keyTokenKey);
        pdc.remove(createdAtKey);
    }

    private ReadResult read(PersistentDataContainer pdc) {
        String rawId = pdc.get(lockIdKey, PersistentDataType.STRING);
        boolean any = rawId != null || pdc.has(versionKey) || pdc.has(ownerIdKey) || pdc.has(hashKey);
        if (!any) {
            return new ReadResult(null, false);
        }
        try {
            Integer version = pdc.get(versionKey, PersistentDataType.INTEGER);
            String rawOwner = pdc.get(ownerIdKey, PersistentDataType.STRING);
            String ownerName = pdc.get(ownerNameKey, PersistentDataType.STRING);
            byte[] salt = pdc.get(saltKey, PersistentDataType.BYTE_ARRAY);
            byte[] hash = pdc.get(hashKey, PersistentDataType.BYTE_ARRAY);
            Integer iterations = pdc.get(iterationsKey, PersistentDataType.INTEGER);
            Long duration = pdc.get(durationKey, PersistentDataType.LONG);
            String rawKeyToken = pdc.get(keyTokenKey, PersistentDataType.STRING);
            Long createdAt = pdc.get(createdAtKey, PersistentDataType.LONG);
            if (version == null || version != DATA_VERSION || rawId == null || rawOwner == null
                    || ownerName == null || salt == null || hash == null || iterations == null
                    || duration == null || rawKeyToken == null || createdAt == null
                    || iterations < 1 || duration < 1) {
                return new ReadResult(null, true);
            }
            return new ReadResult(new LockData(UUID.fromString(rawId), UUID.fromString(rawOwner), ownerName,
                    salt, hash, iterations, duration, UUID.fromString(rawKeyToken), createdAt), false);
        } catch (IllegalArgumentException exception) {
            return new ReadResult(null, true);
        }
    }

    public Block blockForInventory(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof DoubleChest doubleChest) {
            InventoryHolder left = doubleChest.getLeftSide();
            if (left instanceof org.bukkit.block.Chest chest) {
                return chest.getBlock();
            }
        }
        if (holder instanceof Container container) {
            return container.getBlock();
        }
        return null;
    }

    public NamespacedKey itemLockIdKey() {
        return itemLockIdKey;
    }

    public NamespacedKey itemKeyTokenKey() {
        return itemKeyTokenKey;
    }

    public record ContainerGroup(List<Block> blocks, Inventory inventory) {
        public Block primary() {
            return blocks.getFirst();
        }
    }

    public record Lookup(ContainerGroup group, LockData data, boolean corrupt) {
        static Lookup unsupported() {
            return new Lookup(null, null, false);
        }

        public boolean supported() {
            return group != null;
        }

        public boolean secured() {
            return data != null || corrupt;
        }
    }

    private record ReadResult(LockData data, boolean corrupt) {
        boolean secured() {
            return data != null || corrupt;
        }
    }
}
