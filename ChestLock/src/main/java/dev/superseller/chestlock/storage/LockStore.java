package dev.superseller.chestlock.storage;

import dev.superseller.chestlock.ChestLockPlugin;
import dev.superseller.chestlock.config.PluginConfig;
import dev.superseller.chestlock.model.BlockRef;
import dev.superseller.chestlock.model.LockData;
import io.papermc.paper.block.TileStateInventoryHolder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.TileState;
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
    private final NamespacedKey accessBlocksKey;
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
        accessBlocksKey = new NamespacedKey(plugin, "access_blocks");
        itemLockIdKey = new NamespacedKey(plugin, "key_lock_id");
        itemKeyTokenKey = new NamespacedKey(plugin, "key_token");
    }

    /**
     * Classifies the family of a lockable container material.
     *
     * <p>Ender chests are intentionally excluded: their contents are per-player and they cannot
     * exchange items with automation blocks. Minecart containers are entities, not blocks, and
     * cannot carry block metadata.</p>
     */
    public static Family family(Material material) {
        if (material == null) {
            return Family.NONE;
        }
        String name = material.name();
        if (material == Material.CHEST || material == Material.TRAPPED_CHEST) {
            return Family.CHEST;
        }
        if (material == Material.COPPER_CHEST || name.endsWith("_COPPER_CHEST")) {
            return Family.COPPER_CHEST;
        }
        if (material == Material.BARREL) {
            return Family.BARREL;
        }
        if (material == Material.SHULKER_BOX || name.endsWith("_SHULKER_BOX")) {
            return Family.SHULKER_BOX;
        }
        if (material == Material.FURNACE || material == Material.BLAST_FURNACE || material == Material.SMOKER) {
            return Family.FURNACE;
        }
        if (material == Material.DISPENSER || material == Material.DROPPER) {
            return Family.DISPENSER;
        }
        if (material == Material.HOPPER) {
            return Family.HOPPER;
        }
        if (material == Material.BREWING_STAND) {
            return Family.BREWING_STAND;
        }
        if (material == Material.CRAFTER) {
            return Family.CRAFTER;
        }
        if (material == Material.CHISELED_BOOKSHELF) {
            return Family.CHISELED_BOOKSHELF;
        }
        if (material == Material.DECORATED_POT) {
            return Family.DECORATED_POT;
        }
        if (material == Material.LECTERN) {
            return Family.LECTERN;
        }
        if (name.endsWith("_SHELF")) {
            return Family.SHELF;
        }
        if (material == Material.JUKEBOX) {
            return Family.JUKEBOX;
        }
        return Family.NONE;
    }

    public boolean isContainer(Material material) {
        return family(material) != Family.NONE;
    }

    /** Blocks that can move items into and out of containers and therefore be granted access. */
    public static boolean isAutomationBlock(Material material) {
        return material == Material.HOPPER || material == Material.DROPPER || material == Material.DISPENSER;
    }

    public boolean canCreateLock(Material material, PluginConfig config) {
        return switch (family(material)) {
            case CHEST -> config.lockChests();
            case COPPER_CHEST -> config.lockCopperChests();
            case BARREL -> config.lockBarrels();
            case SHULKER_BOX -> config.lockShulkerBoxes();
            case FURNACE -> config.lockFurnaces();
            case DISPENSER -> config.lockDispensers();
            case HOPPER -> config.lockHoppers();
            case BREWING_STAND -> config.lockBrewingStands();
            case CRAFTER -> config.lockCrafters();
            case CHISELED_BOOKSHELF -> config.lockChiseledBookshelves();
            case DECORATED_POT -> config.lockDecoratedPots();
            case LECTERN -> config.lockLecterns();
            case SHELF -> config.lockShelves();
            case JUKEBOX -> config.lockJukeboxes();
            case NONE -> false;
        };
    }

    public ContainerGroup resolve(Block block) {
        if (!isContainer(block.getType()) || !(block.getState() instanceof TileStateInventoryHolder holder)) {
            return null;
        }
        List<Block> blocks = new ArrayList<>();
        Inventory inventory = holder.getInventory();
        InventoryHolder inventoryHolder = inventory.getHolder();
        if (inventoryHolder instanceof DoubleChest doubleChest) {
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
        if (holder instanceof Chest chest) {
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
            if (!(member.getState() instanceof TileStateInventoryHolder holder)) {
                corrupt = true;
                continue;
            }
            ReadResult read = read(holder.getPersistentDataContainer());
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
            if (!(block.getState() instanceof TileStateInventoryHolder holder)
                    || read(holder.getPersistentDataContainer()).secured()) {
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
            if (!(block.getState() instanceof TileStateInventoryHolder holder)) {
                success = false;
                continue;
            }
            PersistentDataContainer pdc = holder.getPersistentDataContainer();
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
            pdc.set(accessBlocksKey, PersistentDataType.LIST.strings(),
                    data.accessBlocks().stream().map(BlockRef::serialize).toList());
            success &= holder.update(true, false);
        }
        return success;
    }

    public boolean clear(ContainerGroup group) {
        boolean success = true;
        for (Block block : group.blocks()) {
            if (!(block.getState() instanceof TileStateInventoryHolder holder)) {
                success = false;
                continue;
            }
            removeLockMetadata(holder.getPersistentDataContainer());
            success &= holder.update(true, false);
        }
        return success;
    }

    /**
     * Removes copied lock metadata from only this newly placed block. This prevents a shulker-box
     * item or creative clone from duplicating a lock UUID while leaving an adjacent locked chest untouched.
     */
    public boolean clearCopiedMetadata(Block block) {
        if (!(block.getState() instanceof TileStateInventoryHolder holder)
                || !read(holder.getPersistentDataContainer()).secured()) {
            return false;
        }
        removeLockMetadata(holder.getPersistentDataContainer());
        holder.update(true, false);
        return true;
    }

    /** Removes lock metadata embedded in a dropped BlockState item, most notably a shulker box. */
    public boolean clearCopiedMetadata(ItemStack item) {
        if (!(item.getItemMeta() instanceof BlockStateMeta meta)) {
            return false;
        }
        BlockState state = meta.getBlockState();
        if (!(state instanceof TileStateInventoryHolder holder)
                || !read(holder.getPersistentDataContainer()).secured()) {
            return false;
        }
        removeLockMetadata(holder.getPersistentDataContainer());
        meta.setBlockState(state);
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
        pdc.remove(accessBlocksKey);
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
            List<BlockRef> accessBlocks = readAccessBlocks(pdc);
            return new ReadResult(new LockData(UUID.fromString(rawId), UUID.fromString(rawOwner), ownerName,
                    salt, hash, iterations, duration, UUID.fromString(rawKeyToken), createdAt, accessBlocks), false);
        } catch (IllegalArgumentException exception) {
            return new ReadResult(null, true);
        }
    }

    /**
     * Reads the optional access-block whitelist. Locks created before this feature simply have
     * no entry and default to an empty whitelist. Unreadable entries are dropped.
     */
    private List<BlockRef> readAccessBlocks(PersistentDataContainer pdc) {
        List<String> raw = pdc.get(accessBlocksKey, PersistentDataType.LIST.strings());
        if (raw == null) {
            return List.of();
        }
        List<BlockRef> blocks = new ArrayList<>(raw.size());
        for (String entry : raw) {
            BlockRef ref = BlockRef.parse(entry);
            if (ref != null) {
                blocks.add(ref);
            }
        }
        return List.copyOf(blocks);
    }

    public Block blockForInventory(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof DoubleChest doubleChest) {
            InventoryHolder left = doubleChest.getLeftSide();
            if (left instanceof Chest chest) {
                return chest.getBlock();
            }
        }
        if (holder instanceof TileState tileState) {
            return tileState.getBlock();
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

    /** The container families ChestLock understands. */
    public enum Family {
        NONE,
        CHEST,
        COPPER_CHEST,
        BARREL,
        SHULKER_BOX,
        FURNACE,
        DISPENSER,
        HOPPER,
        BREWING_STAND,
        CRAFTER,
        CHISELED_BOOKSHELF,
        DECORATED_POT,
        LECTERN,
        SHELF,
        JUKEBOX
    }
}
