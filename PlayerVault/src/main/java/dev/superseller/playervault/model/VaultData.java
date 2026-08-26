package dev.superseller.playervault.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * The persisted state of one player's vault: how many rows they own and what is
 * stored inside them.
 *
 * <p>Instances live in the {@code VaultService} cache and are touched from the
 * region thread that owns the player as well as from asynchronous save tasks, so
 * every mutating accessor is {@code synchronized} on the instance.
 *
 * <p>All {@link ItemStack}s are defensively cloned on the way in and out, which
 * keeps cached data safe when a live inventory is edited at the same time.
 */
public final class VaultData {

    public static final int SLOTS_PER_ROW = 9;

    private final UUID owner;
    private String name;
    private int rows;
    private int purchasedRows;
    private ItemStack[] items;
    private long updated;
    private double totalSpent;

    public VaultData(UUID owner, String name, int rows) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.name = name == null ? "" : name;
        this.rows = Math.max(1, rows);
        this.items = new ItemStack[this.rows * SLOTS_PER_ROW];
        this.updated = System.currentTimeMillis();
    }

    public UUID owner() {
        return owner;
    }

    public synchronized String name() {
        return name;
    }

    public synchronized void name(String value) {
        this.name = value == null ? "" : value;
    }

    public synchronized int rows() {
        return rows;
    }

    /**
     * How many rows were actually paid for.
     *
     * <p>Permission bonus rows and admin grants are excluded on purpose, so the
     * ladder advertised in {@code config.yml} (10k, 15k, 22.5k, ...) stays exact no
     * matter how a player reached their current size.
     */
    public synchronized int purchasedRows() {
        return purchasedRows;
    }

    public synchronized void purchasedRows(int value) {
        this.purchasedRows = Math.max(0, value);
        touch();
    }

    public synchronized void addPurchasedRows(int count) {
        this.purchasedRows = Math.max(0, this.purchasedRows + count);
        touch();
    }

    public synchronized int capacity() {
        return items.length;
    }

    public synchronized long updated() {
        return updated;
    }

    public synchronized double totalSpent() {
        return totalSpent;
    }

    public synchronized void totalSpent(double value) {
        this.totalSpent = Math.max(0.0d, value);
        touch();
    }

    public synchronized void touch() {
        this.updated = System.currentTimeMillis();
    }

    /**
     * Resizes the vault.
     *
     * <p>Shrinking never destroys items silently: whatever no longer fits is
     * returned so the caller can hand it back to the player.
     *
     * @param newRows the new row count, at least 1
     * @return the items that were evicted, empty when nothing was removed
     */
    public synchronized List<ItemStack> resize(int newRows) {
        int target = Math.max(1, newRows);
        List<ItemStack> evicted = new ArrayList<>();
        if (target == rows) {
            return evicted;
        }
        ItemStack[] next = new ItemStack[target * SLOTS_PER_ROW];
        System.arraycopy(items, 0, next, 0, Math.min(items.length, next.length));
        for (int i = next.length; i < items.length; i++) {
            ItemStack stack = items[i];
            if (stack != null && stack.getType() != Material.AIR) {
                evicted.add(stack.clone());
            }
        }
        items = next;
        rows = target;
        touch();
        return evicted;
    }

    public synchronized ItemStack item(int slot) {
        if (slot < 0 || slot >= items.length) {
            return null;
        }
        ItemStack stack = items[slot];
        return stack == null ? null : stack.clone();
    }

    public synchronized void item(int slot, ItemStack stack) {
        if (slot < 0 || slot >= items.length) {
            return;
        }
        items[slot] = stack == null ? null : stack.clone();
        touch();
    }

    /** A defensive copy of the whole vault. */
    public synchronized ItemStack[] contents() {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            copy[i] = items[i] == null ? null : items[i].clone();
        }
        return copy;
    }

    /** Replaces the whole vault with a defensive copy of {@code source}. */
    public synchronized void contents(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[items.length];
        if (source != null) {
            for (int i = 0; i < Math.min(source.length, copy.length); i++) {
                copy[i] = source[i] == null ? null : source[i].clone();
            }
        }
        items = copy;
        touch();
    }

    public synchronized int usedSlots() {
        int used = 0;
        for (ItemStack stack : items) {
            if (stack != null && stack.getType() != Material.AIR && stack.getAmount() > 0) {
                used++;
            }
        }
        return used;
    }

    /** Removes everything. */
    public synchronized void clear() {
        Arrays.fill(items, null);
        touch();
    }

    /**
     * Adds an item, merging into existing partial stacks first.
     *
     * @return the part that did not fit, or {@code null} when everything was stored
     */
    public synchronized ItemStack add(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
            return null;
        }
        ItemStack remaining = stack.clone();
        int max = Math.max(1, remaining.getMaxStackSize());
        for (int i = 0; i < items.length && remaining.getAmount() > 0; i++) {
            ItemStack current = items[i];
            if (current == null || current.getType() == Material.AIR) {
                continue;
            }
            if (current.isSimilar(remaining) && current.getAmount() < max) {
                int space = max - current.getAmount();
                int move = Math.min(space, remaining.getAmount());
                current.setAmount(current.getAmount() + move);
                remaining.setAmount(remaining.getAmount() - move);
            }
        }
        for (int i = 0; i < items.length && remaining.getAmount() > 0; i++) {
            ItemStack current = items[i];
            if (current == null || current.getType() == Material.AIR) {
                int move = Math.min(max, remaining.getAmount());
                ItemStack stored = remaining.clone();
                stored.setAmount(move);
                items[i] = stored;
                remaining.setAmount(remaining.getAmount() - move);
            }
        }
        touch();
        return remaining.getAmount() > 0 ? remaining : null;
    }

    /**
     * Compacts the vault: stacks are merged, empty slots removed and the result is
     * ordered by material name so the vault looks the same after every sort.
     */
    public synchronized int sort() {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : items) {
            if (stack != null && stack.getType() != Material.AIR && stack.getAmount() > 0) {
                stacks.add(stack.clone());
            }
        }
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack incoming : stacks) {
            ItemStack remaining = incoming;
            for (ItemStack target : merged) {
                if (remaining.getAmount() <= 0) {
                    break;
                }
                int max = Math.max(1, target.getMaxStackSize());
                if (target.isSimilar(remaining) && target.getAmount() < max) {
                    int move = Math.min(max - target.getAmount(), remaining.getAmount());
                    target.setAmount(target.getAmount() + move);
                    remaining.setAmount(remaining.getAmount() - move);
                }
            }
            if (remaining.getAmount() > 0) {
                merged.add(remaining);
            }
        }
        merged.sort(Comparator
                .comparing((ItemStack stack) -> stack.getType().name())
                .thenComparing(ItemStack::getAmount, Comparator.<Integer>reverseOrder()));
        Arrays.fill(items, null);
        for (int i = 0; i < merged.size() && i < items.length; i++) {
            items[i] = merged.get(i);
        }
        touch();
        return usedSlots();
    }
}
