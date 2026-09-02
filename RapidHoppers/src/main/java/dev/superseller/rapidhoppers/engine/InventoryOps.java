package dev.superseller.rapidhoppers.engine;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Low level, null-safe inventory moves shared by every accelerated container. */
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
        if (from == null || to == null || amount <= 0) {
            return 0;
        }
        ItemStack[] contents = from.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            int free = freeSpaceFor(to, stack);
            if (free <= 0) {
                continue;
            }
            int move = TransferMath.moveAmount(amount, stack.getAmount(), free, stack.getMaxStackSize());
            if (move <= 0) {
                continue;
            }
            ItemStack moving = stack.clone();
            moving.setAmount(move);
            if (!to.addItem(moving).isEmpty()) {
                // destination changed underneath us - abort this slot
                continue;
            }
            int remaining = stack.getAmount() - move;
            if (remaining <= 0) {
                from.setItem(slot, null);
            } else {
                ItemStack left = stack.clone();
                left.setAmount(remaining);
                from.setItem(slot, left);
            }
            return move;
        }
        return 0;
    }

    /** How many more items of {@code probe}'s type fit into {@code inv}. */
    public static int freeSpaceFor(Inventory inv, ItemStack probe) {
        if (inv == null || probe == null) {
            return 0;
        }
        int max = probe.getMaxStackSize();
        int free = 0;
        for (ItemStack slot : inv.getStorageContents()) {
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
        for (ItemStack stack : inv.getStorageContents()) {
            if (stack != null && !stack.getType().isAir() && stack.getAmount() > 0) {
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
        for (ItemStack stack : inv.getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                return true;
            }
            if (stack.getAmount() < stack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }
}
