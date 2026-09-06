package dev.superseller.rapidhoppers.engine;

import java.util.Map;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.InventoryType;

/**
 * Low level, null-safe inventory moves shared by every accelerated container.
 *
 * <p>Every move here is a <em>single item</em>, exactly like a vanilla hopper
 * transfer. RapidHoppers only changes how often a transfer happens, never how
 * much moves, so no contraption ever sees an amount it could not have seen in
 * vanilla.</p>
 *
 * <p>Every path that adds an item also subtracts <em>exactly</em> that many from
 * the source, and nothing is ever moved into a container type vanilla would not
 * insert into.</p>
 */
public final class InventoryOps {

    private InventoryOps() {
    }

    /**
     * Moves a single item from the first movable slot of {@code from} into
     * {@code to}, exactly like one vanilla hopper transfer.
     *
     * @return 1 when an item moved, 0 otherwise
     */
    public static int moveOne(Inventory from, Inventory to) {
        if (!canMove(from, to)) {
            return 0;
        }
        int size = from.getSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = from.getItem(slot);
            if (!isMovable(stack) || !accepts(to, stack)) {
                continue;
            }
            if (transferSlot(from, to, slot, stack) > 0) {
                return 1;
            }
        }
        return 0;
    }

    /**
     * Moves a single item similar to {@code probe} out of {@code from} into
     * {@code to}. Used when replacing a vanilla transfer so only the item type
     * vanilla already selected is touched.
     *
     * @return 1 when an item moved, 0 otherwise
     */
    public static int moveOneSimilar(Inventory from, Inventory to, ItemStack probe) {
        if (!canMove(from, to) || !isMovable(probe) || !accepts(to, probe)) {
            return 0;
        }
        int size = from.getSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = from.getItem(slot);
            if (!isMovable(stack) || !stack.isSimilar(probe)) {
                continue;
            }
            if (transferSlot(from, to, slot, stack) > 0) {
                return 1;
            }
        }
        return 0;
    }

    /**
     * Adds a single item of {@code stack}'s type into {@code to}.
     * Does not mutate {@code stack}; the caller shrinks the source (item
     * entity, slot, …) by the returned count.
     *
     * @return 1 when an item was added, 0 otherwise
     */
    public static int addOne(Inventory to, ItemStack stack) {
        if (to == null || !isMovable(stack) || !accepts(to, stack)) {
            return 0;
        }
        if (freeSpaceFor(to, stack) <= 0) {
            return 0;
        }
        ItemStack moving = stack.clone();
        moving.setAmount(1);
        return deposited(to.addItem(moving), 1);
    }

    /** How many more items of {@code probe}'s type fit into {@code inv}. */
    public static int freeSpaceFor(Inventory inv, ItemStack probe) {
        if (inv == null || probe == null || !accepts(inv, probe)) {
            return 0;
        }
        int max = Math.max(1, probe.getMaxStackSize());
        int free = 0;
        ItemStack[] contents = inv.getStorageContents();
        if (contents == null) {
            return 0;
        }
        for (ItemStack slot : contents) {
            if (slot == null || slot.getType().isAir()) {
                free += max;
            } else if (slot.isSimilar(probe)) {
                free += Math.max(0, slot.getMaxStackSize() - slot.getAmount());
            }
            if (free >= max) {
                return free;
            }
        }
        return free;
    }

    /** True when the inventory contains at least one non-air item. */
    public static boolean hasItems(Inventory inv) {
        if (inv == null) {
            return false;
        }
        ItemStack[] contents = inv.getStorageContents();
        if (contents == null) {
            return false;
        }
        for (ItemStack stack : contents) {
            if (isMovable(stack)) {
                return true;
            }
        }
        return false;
    }

    /** True when the inventory has room for at least one more item. */
    public static boolean hasSpace(Inventory inv) {
        if (inv == null) {
            return false;
        }
        ItemStack[] contents = inv.getStorageContents();
        if (contents == null) {
            return false;
        }
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType().isAir()) {
                return true;
            }
            if (stack.getAmount() < stack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hoppers, chests, barrels, shulkers, droppers and dispensers. Furnaces,
     * brewers, crafters, jukeboxes, lecterns, chiseled bookshelves and similar
     * have slot rules {@code addItem} would happily bypass (pulling fuel,
     * stuffing the result slot, inserting items the block cannot hold), so
     * RapidHoppers leaves them entirely to vanilla.
     */
    public static boolean isSimpleStorage(Inventory inv) {
        if (inv == null) {
            return false;
        }
        InventoryType type = inv.getType();
        if (type == null) {
            return false;
        }
        return switch (type.name()) {
            case "CHEST", "BARREL", "SHULKER_BOX", "HOPPER", "DROPPER", "DISPENSER" -> true;
            default -> false;
        };
    }

    /**
     * Vanilla's insertion rules for the item/destination pair.
     *
     * <p>Shulker boxes never accept another shulker box, and nothing that is
     * not simple storage accepts anything at all.</p>
     */
    public static boolean accepts(Inventory to, ItemStack stack) {
        if (to == null || stack == null) {
            return false;
        }
        if (!isSimpleStorage(to)) {
            return false;
        }
        return !(isShulkerBox(stack) && isShulkerInventory(to));
    }

    public static boolean sameInventory(Inventory a, Inventory b) {
        if (a == null || b == null) {
            return false;
        }
        if (a == b) {
            return true;
        }
        InventoryHolder ha = a.getHolder();
        InventoryHolder hb = b.getHolder();
        return ha != null && ha == hb;
    }

    public static boolean isShulkerBox(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        String name = stack.getType().name();
        return name.endsWith("SHULKER_BOX");
    }

    public static boolean isShulkerInventory(Inventory inv) {
        if (inv == null || inv.getType() == null) {
            return false;
        }
        return "SHULKER_BOX".equals(inv.getType().name());
    }

    public static int countSimilar(Inventory inv, ItemStack probe) {
        if (inv == null || probe == null) {
            return 0;
        }
        int total = 0;
        ItemStack[] contents = inv.getStorageContents();
        if (contents == null) {
            return 0;
        }
        for (ItemStack stack : contents) {
            if (stack != null && stack.isSimilar(probe)) {
                total += Math.max(0, stack.getAmount());
            }
        }
        return total;
    }

    // --- internals ----------------------------------------------------------

    private static boolean canMove(Inventory from, Inventory to) {
        return from != null && to != null
                && !sameInventory(from, to)
                && isSimpleStorage(from)
                && isSimpleStorage(to);
    }

    private static boolean isMovable(ItemStack stack) {
        return stack != null && !stack.getType().isAir() && stack.getAmount() > 0;
    }

    /**
     * Moves one item out of a source slot, shrinking that slot by however many
     * the destination actually accepted (0 or 1).
     */
    private static int transferSlot(Inventory from, Inventory to, int slot, ItemStack stack) {
        int move = TransferMath.moveAmount(1, stack.getAmount(), freeSpaceFor(to, stack));
        if (move <= 0) {
            return 0;
        }
        ItemStack moving = stack.clone();
        moving.setAmount(move);
        int added = deposited(to.addItem(moving), move);
        if (added <= 0) {
            return 0;
        }
        int remaining = stack.getAmount() - added;
        if (remaining <= 0) {
            from.setItem(slot, null);
        } else {
            ItemStack left = stack.clone();
            left.setAmount(remaining);
            from.setItem(slot, left);
        }
        return added;
    }

    /** Items that {@code addItem} actually kept, given what we attempted. */
    static int deposited(Map<Integer, ItemStack> leftover, int attempted) {
        int left = 0;
        if (leftover != null) {
            for (ItemStack stack : leftover.values()) {
                if (stack != null && !stack.getType().isAir()) {
                    left += Math.max(0, stack.getAmount());
                }
            }
        }
        return Math.max(0, attempted - left);
    }
}
