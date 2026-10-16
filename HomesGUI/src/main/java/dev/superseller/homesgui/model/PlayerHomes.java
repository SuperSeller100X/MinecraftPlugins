package dev.superseller.homesgui.model;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/** Thread-safe home and slot state for one player. */
public final class PlayerHomes {

    private final UUID owner;
    private final NavigableMap<Integer, HomeLocation> homes = new TreeMap<>();
    private int purchasedSlots;
    private int bonusSlots;

    public PlayerHomes(UUID owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    public UUID owner() {
        return owner;
    }

    public synchronized HomeLocation home(int slot) {
        return homes.get(slot);
    }

    public synchronized boolean hasHome(int slot) {
        return homes.containsKey(slot);
    }

    public synchronized void putHome(int slot, HomeLocation location) {
        if (slot < 1) {
            throw new IllegalArgumentException("slot must be positive");
        }
        homes.put(slot, Objects.requireNonNull(location, "location"));
    }

    public synchronized HomeLocation removeHome(int slot) {
        return homes.remove(slot);
    }

    public synchronized void clearHomes() {
        homes.clear();
    }

    public synchronized int savedCount() {
        return homes.size();
    }

    public synchronized int highestSavedSlot() {
        return homes.isEmpty() ? 0 : homes.lastKey();
    }

    public synchronized Map<Integer, HomeLocation> homes() {
        return Collections.unmodifiableMap(new TreeMap<>(homes));
    }

    public synchronized int purchasedSlots() {
        return purchasedSlots;
    }

    public synchronized void purchasedSlots(int value) {
        purchasedSlots = Math.max(0, value);
    }

    public synchronized void addPurchasedSlots(int amount) {
        purchasedSlots = Math.max(0, Math.addExact(purchasedSlots, amount));
    }

    public synchronized int bonusSlots() {
        return bonusSlots;
    }

    public synchronized void bonusSlots(int value) {
        bonusSlots = Math.max(0, value);
    }

    public synchronized void addBonusSlots(int amount) {
        bonusSlots = Math.max(0, Math.addExact(bonusSlots, amount));
    }

    /** Immutable serialization view; no Bukkit objects escape into async I/O. */
    public synchronized Snapshot snapshot() {
        return new Snapshot(owner, new TreeMap<>(homes), purchasedSlots, bonusSlots);
    }

    public record Snapshot(UUID owner, Map<Integer, HomeLocation> homes, int purchasedSlots, int bonusSlots) {
        public Snapshot {
            Objects.requireNonNull(owner, "owner");
            homes = Collections.unmodifiableMap(new TreeMap<>(homes));
            purchasedSlots = Math.max(0, purchasedSlots);
            bonusSlots = Math.max(0, bonusSlots);
        }
    }
}
