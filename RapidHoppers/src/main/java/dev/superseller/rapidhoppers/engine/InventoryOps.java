package dev.superseller.rapidhoppers.engine;

import java.util.Map;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.InventoryType;

/**
 * Low level, null-safe inventory moves shared by every accelerated container.
 *
 * <p>Every path that adds items also subtracts <em>exactly</em> that many from
 * the source. Partial {@code addItem} leftovers used to leave the extra copy
 * in the destination — that is a dupe, and it is no longer possible.</p>
 */
public final class InventoryOps {

    private InventoryOps() {
    }

    /**
     * Moves up to {@code amount} items of the first movable stack from
     * {@code from} into {@code to}.
     *
     * @return the number of items actually moved (0 when nothing could move)
     */
    public static int moveOneStack(Inventory from, Inventory to, int amount) {
        if (!canMove(from, to, amount)) {
            return 0;
        }
        int size = from.getSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = from.getItem(slot);
            if (!isMovable(stack)) {
                continue;
            }
            if (isShulkerBox(stack) && isShulkerInventory(to)) {
                continue;
            }
            int moved = transferSlot(from, to, slot, stack, amount);
            if (moved > 0) {
                return moved;
            }
        }
        return 0;
    }

    /**
     * Moves up to {@code amount} items similar to {@code probe} from
     * {@code from} into {@code to}. Used when replacing a vanilla transfer so
     * only the item type vanilla already selected is touched.
     */
    public static int moveSimilar(Inventory from, Inventory to, ItemStack probe, int amount) {
        if (!canMove(from, to, amount) || probe == null || probe.getType().isAir()) {
            return 0;
        }
        if (isShulkerBox(probe) && isShulkerInventory(to)) {
            return 0;
        }
        int remaining = amount;
        int movedTotal = 0;
        int size = from.getSize();
        for (int slot = 0; slot < size && remaining > 0; slot++) {
            ItemStack stack = from.getItem(slot);
            if (!isMovable(stack) || !stack.isSimilar(probe)) {
                continue;
            }
            int moved = transferSlot(from, to, slot, stack, remaining);
            if (moved > 0) {
                movedTotal += moved;
                remaining -= moved;
            }
        }
        return movedTotal;
    }

    /**
     * Adds up to {@code amount} items of {@code stack} into {@code to}.
     * Does not mutate {@code stack}; the caller is responsible for shrinking
     * the source (item entity, slot, …) by the returned count.
     */
    public static int addUpTo(Inventory to, ItemStack stack, int amount) {
        if (to == null || !isMovable(stack) || amount <= 0) {
            return 0;
        }
        if (isShulkerBox(stack) && isShulkerInventory(to)) {
            return 0;
        }
        int free = freeSpaceFor(to, stack);
        int move = TransferMath.moveAmount(amount, stack.getAmount(), free, stack.getMaxStackSize());
        if (move <= 0) {
            return 0;
        }
        ItemStack moving = stack.clone();
        moving.setAmount(move);
        return deposited(to.addItem(moving), move);
    }

    /** How many more items of {@code probe}'s type fit into {@code inv}. */
    public static int freeSpaceFor(Inventory inv, ItemStack probe) {
        if (inv == null || probe == null) {
            return 0;
        }
        if (isShulkerBox(probe) && isShulkerInventory(inv)) {
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
     * brewers, crafters and similar have slot rules {@code addItem} would
     * bypass (pulling fuel, stuffing the result slot, …).
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

    private static boolean canMove(Inventory from, Inventory to, int amount) {
        return from != null && to != null && amount > 0
                && !sameInventory(from, to)
                && isSimpleStorage(from)
                && isSimpleStorage(to);
    }

    private static boolean isMovable(ItemStack stack) {
        return stack != null && !stack.getType().isAir() && stack.getAmount() > 0;
    }

    /**
     * Moves items out of one source slot, shrinking that slot by however many
     * the destination actually accepted.
     */
    private static int transferSlot(Inventory from, Inventory to, int slot, ItemStack stack, int amount) {
        int free = freeSpaceFor(to, stack);
        int move = TransferMath.moveAmount(amount, stack.getAmount(), free, stack.getMaxStackSize());
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
