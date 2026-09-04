package dev.superseller.rapidhoppers.smoke;

import java.util.HashMap;
import java.util.List;

import dev.superseller.rapidhoppers.config.Settings;
import dev.superseller.rapidhoppers.config.Settings.ContainerType;
import dev.superseller.rapidhoppers.config.Settings.WorldMode;
import dev.superseller.rapidhoppers.engine.InventoryOps;
import dev.superseller.rapidhoppers.engine.Stats;
import dev.superseller.rapidhoppers.engine.TransferMath;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/** Server-free checks of the engine math, settings and inventory moves. */
public final class EngineTest {

    static int checks;

    public static void main(String[] args) {
        testTransferMath();
        testSettings();
        testInventoryOps();
        testStats();
        System.out.println("EngineTest: " + checks + " checks passed");
    }

    private static void testTransferMath() {
        eq(8, TransferMath.effectiveInterval(4, 2, false, 3), "interval with type factor");
        eq(24, TransferMath.effectiveInterval(4, 2, true, 3), "interval while throttled");
        eq(1, TransferMath.effectiveInterval(0, 0, false, 0), "interval never below 1");
        eq(32.0D, TransferMath.speedFactor(2, 8), "speed factor 2 ticks / 8 items");
        eq(1.0D, TransferMath.speedFactor(8, 1), "vanilla speed factor");

        eq(5, TransferMath.moveAmount(8, 5, 64, 64), "limited by availability");
        eq(3, TransferMath.moveAmount(8, 20, 3, 64), "limited by destination space");
        eq(16, TransferMath.moveAmount(64, 64, 64, 16), "limited by max stack size");
        eq(0, TransferMath.moveAmount(8, 0, 64, 64), "nothing to move");
        eq(0, TransferMath.moveAmount(8, 5, -1, 64), "negative space clamps to 0");

        eq(8, TransferMath.replaceAmount(8, 1, 8, 64, 64), "replace vanilla 1 with configured 8");
        eq(1, TransferMath.replaceAmount(8, 1, 1, 64, 64), "cannot take more than source has");
        eq(0, TransferMath.replaceAmount(8, 1, 0, 64, 64), "empty source is not boosted");
        eq(1, TransferMath.replaceAmount(1, 1, 5, 64, 64), "items-per-transfer 1 leaves vanilla alone");
        eq(4, TransferMath.replaceAmount(8, 1, 20, 4, 64), "replacement limited by dest space");

        yes(TransferMath.shouldThrottle(17.0D, 18.0D, true), "throttle below soft tps");
        no(TransferMath.shouldThrottle(17.0D, 18.0D, false), "throttle disabled");
        yes(TransferMath.shouldPause(13.0D, 14.0D, true), "pause below hard tps");
        no(TransferMath.shouldPause(15.0D, 14.0D, true), "no pause above hard tps");

        eq(3, TransferMath.chunkDistance(0, 0, 3, 1), "chebyshev distance");
        eq(1.2D, TransferMath.round1(1.24D), "round down");
        eq(1.3D, TransferMath.round1(1.25D), "round up");
    }

    private static void testSettings() {
        Settings settings = new Settings();
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("enabled", Boolean.TRUE);
        cfg.set("engine.interval-ticks", 99);
        cfg.set("engine.items-per-transfer", 999);
        cfg.set("worlds.mode", "whitelist");
        cfg.set("worlds.list", List.of("world_nether"));
        cfg.set("performance.throttle.soft-tps", 15.0D);
        cfg.set("performance.throttle.hard-tps", 19.0D);
        settings.load(cfg);

        eq(Settings.MAX_INTERVAL, settings.getIntervalTicks(), "interval clamped to max");
        eq(Settings.MAX_STACK, settings.getItemsPerTransfer(), "stack clamped to max");
        yes(settings.getWorldMode() == WorldMode.WHITELIST, "world mode parsed case-insensitively");
        yes(settings.appliesToWorld("world_nether"), "whitelisted world accelerated");
        no(settings.appliesToWorld("world"), "unlisted world not accelerated in whitelist mode");
        yes(settings.getThrottleHardTps() <= settings.getThrottleSoftTps(), "hard tps clamped below soft");

        settings.setWorldMode(WorldMode.BLACKLIST);
        no(settings.appliesToWorld("world_nether"), "blacklisted world skipped");
        yes(settings.appliesToWorld("world"), "unlisted world accelerated in blacklist mode");

        settings.setIntervalTicks(0);
        eq(Settings.MIN_INTERVAL, settings.getIntervalTicks(), "interval clamped to min");
        settings.setIntervalTicks(2);
        eq(4.0D, settings.speedFactor(), "speed factor from interval");

        settings.setContainerEnabled(ContainerType.DISPENSER, true);
        yes(settings.isContainerEnabled(ContainerType.DISPENSER), "container toggle");
        yes(ContainerType.byName("hopper-minecart") == ContainerType.HOPPER_MINECART, "type by dashed name");
        yes(ContainerType.byName("HOPPER_MINECART") == ContainerType.HOPPER_MINECART, "type by enum name");
        yes(ContainerType.byName("nope") == null, "unknown type");

        settings.setThrottleSoftTps(10.0D);
        yes(settings.getThrottleHardTps() <= 10.0D, "lowering soft tps lowers hard tps");
    }

    private static void testInventoryOps() {
        FakeInventory from = new FakeInventory(5);
        FakeInventory to = new FakeInventory(5);
        from.setItem(0, new ItemStack(Material.STONE, 40));

        eq(8, InventoryOps.moveOneStack(from, to, 8), "moved 8 items");
        eq(32, from.getItem(0).getAmount(), "source reduced");
        eq(8, to.total(Material.STONE), "destination increased");

        eq(32, InventoryOps.moveOneStack(from, to, 64), "moves only what exists");
        yes(from.getItem(0) == null, "source slot cleared");
        no(InventoryOps.hasItems(from), "empty source detected");
        yes(InventoryOps.hasItems(to), "non-empty destination detected");
        yes(InventoryOps.hasSpace(to), "space detected");
        eq(0, InventoryOps.moveOneStack(from, to, 8), "nothing to move");
        eq(0, InventoryOps.moveOneStack(null, to, 8), "null source is safe");
        eq(0, InventoryOps.moveOneStack(from, to, 0), "zero amount is safe");

        FakeInventory full = new FakeInventory(1);
        full.setItem(0, new ItemStack(Material.HOPPER, 64));
        no(InventoryOps.hasSpace(full), "full inventory has no space");
        eq(0, InventoryOps.freeSpaceFor(full, new ItemStack(Material.STONE, 1)), "no space for other type");

        FakeInventory src = new FakeInventory(1);
        FakeInventory dest = new FakeInventory(1);
        src.setItem(0, new ItemStack(Material.STONE, 40));
        dest.setItem(0, new ItemStack(Material.STONE, 62));
        eq(2, InventoryOps.moveOneStack(src, dest, 8), "partial fill does not dupe");
        eq(38, src.getItem(0).getAmount(), "source reduced by deposited amount only");
        eq(64, dest.getItem(0).getAmount(), "destination filled to max");

        FakeInventory self = new FakeInventory(2);
        self.setItem(0, new ItemStack(Material.STONE, 16));
        eq(0, InventoryOps.moveOneStack(self, self, 8), "self-transfer is refused");
        eq(16, self.getItem(0).getAmount(), "self-transfer did not clone items");

        FakeInventory mixedFrom = new FakeInventory(2);
        FakeInventory mixedTo = new FakeInventory(2);
        mixedFrom.setItem(0, new ItemStack(Material.HOPPER, 10));
        mixedFrom.setItem(1, new ItemStack(Material.STONE, 10));
        eq(4, InventoryOps.moveSimilar(mixedFrom, mixedTo, new ItemStack(Material.STONE, 1), 4),
                "moveSimilar only takes the probed type");
        eq(10, mixedFrom.getItem(0).getAmount(), "unrelated slot untouched");
        eq(6, mixedFrom.getItem(1).getAmount(), "similar slot reduced");
        eq(4, mixedTo.total(Material.STONE), "destination received probed type");
        eq(0, mixedTo.total(Material.HOPPER), "destination did not receive other type");

        yes(InventoryOps.sameInventory(self, self), "same instance is the same inventory");
        no(InventoryOps.sameInventory(src, dest), "distinct inventories are not the same");
        yes(InventoryOps.isSimpleStorage(src), "hopper inventory is simple storage");
        eq(0, InventoryOps.addUpTo(dest, new ItemStack(Material.STONE, 8), 8),
                "addUpTo refuses when destination is full");
    }

    private static void testStats() {
        Stats stats = new Stats();
        stats.recordTransfer(8);
        stats.recordTransfer(4);
        eq(2L, stats.totalTransfers(), "transfer count");
        eq(12L, stats.totalItems(), "item count");
        stats.setTrackedContainers(7);
        eq(7, stats.trackedContainers(), "tracked containers");
        stats.tickWindow();
        yes(stats.ratePerSecond() >= 0.0D, "rate computed");
        stats.reset();
        eq(0L, stats.totalTransfers(), "reset clears counters");
    }

    // --- tiny assertion helpers --------------------------------------------

    static void eq(long expected, long actual, String what) {
        checks++;
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + " but got " + actual);
        }
    }

    static void eq(double expected, double actual, String what) {
        checks++;
        if (Math.abs(expected - actual) > 1.0E-6D) {
            throw new AssertionError(what + ": expected " + expected + " but got " + actual);
        }
    }

    static void yes(boolean condition, String what) {
        checks++;
        if (!condition) {
            throw new AssertionError(what + ": expected true");
        }
    }

    static void no(boolean condition, String what) {
        checks++;
        if (condition) {
            throw new AssertionError(what + ": expected false");
        }
    }

    /** Minimal in-memory inventory backed by the compile stubs. */
    static final class FakeInventory implements org.bukkit.inventory.Inventory {
        private final ItemStack[] items;

        FakeInventory(int size) {
            this.items = new ItemStack[size];
        }

        int total(Material material) {
            int sum = 0;
            for (ItemStack stack : items) {
                if (stack != null && stack.getType() == material) {
                    sum += stack.getAmount();
                }
            }
            return sum;
        }

        @Override
        public int getSize() {
            return items.length;
        }

        @Override
        public org.bukkit.event.inventory.InventoryType getType() {
            return org.bukkit.event.inventory.InventoryType.HOPPER;
        }

        @Override
        public org.bukkit.Location getLocation() {
            return null;
        }

        @Override
        public org.bukkit.inventory.InventoryHolder getHolder() {
            return null;
        }

        @Override
        public ItemStack getItem(int index) {
            return items[index];
        }

        @Override
        public void setItem(int index, ItemStack item) {
            items[index] = item;
        }

        @Override
        public ItemStack[] getContents() {
            return items;
        }

        @Override
        public ItemStack[] getStorageContents() {
            return items;
        }

        @Override
        public HashMap<Integer, ItemStack> addItem(ItemStack... additions) {
            HashMap<Integer, ItemStack> leftovers = new HashMap<>();
            for (int a = 0; a < additions.length; a++) {
                ItemStack adding = additions[a];
                int remaining = adding.getAmount();
                for (int i = 0; i < items.length && remaining > 0; i++) {
                    ItemStack slot = items[i];
                    if (slot == null) {
                        int put = Math.min(remaining, adding.getMaxStackSize());
                        items[i] = new ItemStack(adding.getType(), put);
                        remaining -= put;
                    } else if (slot.isSimilar(adding)) {
                        int put = Math.min(remaining, slot.getMaxStackSize() - slot.getAmount());
                        slot.setAmount(slot.getAmount() + put);
                        remaining -= put;
                    }
                }
                if (remaining > 0) {
                    leftovers.put(a, new ItemStack(adding.getType(), remaining));
                }
            }
            return leftovers;
        }

        @Override
        public List<org.bukkit.entity.HumanEntity> getViewers() {
            return List.of();
        }

        @Override
        public void clear() {
            for (int i = 0; i < items.length; i++) {
                items[i] = null;
            }
        }
    }
}
