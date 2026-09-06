package dev.superseller.rapidhoppers.smoke;

import java.util.HashMap;
import java.util.List;

import dev.superseller.rapidhoppers.config.Settings;
import dev.superseller.rapidhoppers.config.Settings.ContainerType;
import dev.superseller.rapidhoppers.config.Settings.WorldMode;
import dev.superseller.rapidhoppers.engine.InventoryOps;
import dev.superseller.rapidhoppers.engine.Stats;
import dev.superseller.rapidhoppers.engine.TransferClock;
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
        testTransferClock();
        testDropperIntoHopper();
        testStats();
        System.out.println("EngineTest: " + checks + " checks passed");
    }

    private static void testTransferMath() {
        eq(4, TransferMath.effectiveInterval(4, false, 3), "interval unchanged when not throttled");
        eq(8, TransferMath.effectiveInterval(4, true, 3), "throttled interval capped at vanilla");
        eq(6, TransferMath.effectiveInterval(2, true, 3), "throttled interval scaled");
        eq(1, TransferMath.effectiveInterval(0, false, 0), "interval never below 1");
        eq(8, TransferMath.effectiveInterval(99, false, 1), "interval never above vanilla");

        eq(4.0D, TransferMath.speedFactor(2), "2 ticks is 4x vanilla");
        eq(1.0D, TransferMath.speedFactor(8), "8 ticks is vanilla speed");
        eq(8.0D, TransferMath.speedFactor(1), "1 tick is 8x vanilla");
        eq(2.5D, TransferMath.itemsPerSecond(8), "vanilla moves 2.5 items/s");
        eq(10.0D, TransferMath.itemsPerSecond(2), "interval 2 moves 10 items/s");
        eq(20.0D, TransferMath.itemsPerSecond(1), "interval 1 moves 20 items/s");

        // A transfer is always a single item, exactly like vanilla.
        eq(1, TransferMath.moveAmount(1, 5, 64), "one item moves");
        eq(1, TransferMath.moveAmount(64, 40, 64), "requests above 1 are clamped to 1");
        eq(0, TransferMath.moveAmount(1, 0, 64), "nothing to move");
        eq(0, TransferMath.moveAmount(1, 5, 0), "no destination space");
        eq(0, TransferMath.moveAmount(1, 5, -1), "negative space clamps to 0");
        eq(0, TransferMath.moveAmount(0, 5, 64), "zero request moves nothing");

        yes(TransferMath.isReady(100L, Long.MIN_VALUE, 2), "never-moved container is ready");
        yes(TransferMath.isReady(100L, 98L, 2), "cooldown elapsed");
        no(TransferMath.isReady(100L, 99L, 2), "still on cooldown");
        no(TransferMath.isReady(100L, 100L, 1), "just moved this tick");
        yes(TransferMath.isReady(101L, 100L, 1), "interval 1 is ready next tick");

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
        cfg.set("worlds.mode", "whitelist");
        cfg.set("worlds.list", List.of("world_nether"));
        cfg.set("performance.throttle.soft-tps", 15.0D);
        cfg.set("performance.throttle.hard-tps", 19.0D);
        settings.load(cfg);

        eq(Settings.MAX_INTERVAL, settings.getIntervalTicks(), "interval clamped to max");
        eq(1, settings.getItemsPerTransfer(), "always exactly one item per transfer");
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
        eq(10.0D, settings.itemsPerSecond(), "items per second from interval");
        eq(1, settings.getItemsPerTransfer(), "items per transfer is not configurable");

        settings.setContainerEnabled(ContainerType.HOPPER_MINECART, false);
        no(settings.isContainerEnabled(ContainerType.HOPPER_MINECART), "container toggle");
        settings.setContainerEnabled(ContainerType.HOPPER_MINECART, true);
        eq(2, ContainerType.values().length, "only hopper-clocked containers are accelerated");
        yes(ContainerType.byName("dropper") == null, "droppers are left to vanilla");
        yes(ContainerType.byName("dispenser") == null, "dispensers are left to vanilla");
        yes(ContainerType.byName("chest-minecart") == null, "chest minecarts are left to vanilla");
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

        eq(1, InventoryOps.moveOne(from, to), "moved exactly one item");
        eq(39, from.getItem(0).getAmount(), "source reduced by one");
        eq(1, to.total(Material.STONE), "destination received one");

        for (int i = 0; i < 39; i++) {
            InventoryOps.moveOne(from, to);
        }
        yes(from.getItem(0) == null, "source slot cleared after draining one at a time");
        eq(40, to.total(Material.STONE), "every item arrived, none duplicated");
        no(InventoryOps.hasItems(from), "empty source detected");
        yes(InventoryOps.hasItems(to), "non-empty destination detected");
        yes(InventoryOps.hasSpace(to), "space detected");
        eq(0, InventoryOps.moveOne(from, to), "nothing to move");
        eq(0, InventoryOps.moveOne(null, to), "null source is safe");
        eq(0, InventoryOps.moveOne(from, null), "null destination is safe");

        FakeInventory full = new FakeInventory(1);
        full.setItem(0, new ItemStack(Material.HOPPER, 64));
        no(InventoryOps.hasSpace(full), "full inventory has no space");
        eq(0, InventoryOps.freeSpaceFor(full, new ItemStack(Material.STONE, 1)), "no space for other type");
        eq(0, InventoryOps.moveOne(from, full), "cannot move into a full inventory");

        FakeInventory src = new FakeInventory(1);
        FakeInventory dest = new FakeInventory(1);
        src.setItem(0, new ItemStack(Material.STONE, 40));
        dest.setItem(0, new ItemStack(Material.STONE, 63));
        eq(1, InventoryOps.moveOne(src, dest), "tops up a nearly full stack");
        eq(39, src.getItem(0).getAmount(), "source reduced by deposited amount only");
        eq(64, dest.getItem(0).getAmount(), "destination filled to max");
        eq(0, InventoryOps.moveOne(src, dest), "no room left, nothing moves");
        eq(39, src.getItem(0).getAmount(), "refused move did not shrink the source");

        FakeInventory self = new FakeInventory(2);
        self.setItem(0, new ItemStack(Material.STONE, 16));
        eq(0, InventoryOps.moveOne(self, self), "self-transfer is refused");
        eq(16, self.getItem(0).getAmount(), "self-transfer did not clone items");

        FakeInventory mixedFrom = new FakeInventory(2);
        FakeInventory mixedTo = new FakeInventory(2);
        mixedFrom.setItem(0, new ItemStack(Material.HOPPER, 10));
        mixedFrom.setItem(1, new ItemStack(Material.STONE, 10));
        eq(1, InventoryOps.moveOneSimilar(mixedFrom, mixedTo, new ItemStack(Material.STONE, 1)),
                "moveOneSimilar only takes the probed type");
        eq(10, mixedFrom.getItem(0).getAmount(), "unrelated slot untouched");
        eq(9, mixedFrom.getItem(1).getAmount(), "similar slot reduced by one");
        eq(1, mixedTo.total(Material.STONE), "destination received probed type");
        eq(0, mixedTo.total(Material.HOPPER), "destination did not receive other type");

        yes(InventoryOps.sameInventory(self, self), "same instance is the same inventory");
        no(InventoryOps.sameInventory(src, dest), "distinct inventories are not the same");
        yes(InventoryOps.isSimpleStorage(src), "hopper inventory is simple storage");
        yes(InventoryOps.accepts(src, new ItemStack(Material.STONE, 1)), "storage accepts a plain item");
        eq(0, InventoryOps.addOne(dest, new ItemStack(Material.STONE, 8)),
                "addOne refuses when destination is full");
        eq(1, InventoryOps.addOne(mixedTo, new ItemStack(Material.STONE, 8)),
                "addOne adds exactly one item");
        eq(2, mixedTo.total(Material.STONE), "addOne added a single item, not the stack");
    }

    private static void testTransferClock() {
        TransferClock clock = new TransferClock();
        String key = TransferClock.blockKey("world", 1, 2, 3);
        yes("world:1:2:3".equals(key), "block key format");

        yes(clock.isReady(key, 2), "unknown container is ready immediately");
        clock.stamp(key);
        no(clock.isReady(key, 2), "stamped container waits out its cooldown");
        clock.advance();
        no(clock.isReady(key, 2), "still cooling down after one tick");
        clock.advance();
        yes(clock.isReady(key, 2), "ready again after the full interval");

        // A vanilla transfer observed by the listener suppresses ours, so the
        // configured rate is a ceiling rather than a bonus on top of vanilla.
        clock.stamp(key);
        no(clock.isReady(key, 2), "vanilla's own transfer charges the same clock");
        eq(1, clock.size(), "one tracked container");
        clock.clear();
        eq(0, clock.size(), "clear empties the clock");
        yes(TransferClock.entityKey(null) == null, "null entity id is safe");
    }

    /**
     * Regression: a dropper pointed straight into a hopper.
     *
     * <p>Vanilla fires a dropper on a redstone pulse and it ejects exactly ONE
     * item. The old engine treated droppers as an accelerated container family
     * with an items-per-transfer of 8, so a single pulse shoved 8 items into
     * the hopper and, worse, the engine ticked droppers on its own clock and
     * drained them with no redstone at all.</p>
     *
     * <p>Droppers are now not a container family and the engine never ticks
     * them. The only way RapidHoppers can touch a dropper's contents is as the
     * generic single-item move below, which is capped at one item.</p>
     */
    private static void testDropperIntoHopper() {
        // Droppers are no longer an accelerated container family at all.
        yes(ContainerType.byName("dropper") == null, "dropper is not a container family");
        for (ContainerType type : ContainerType.values()) {
            no("dropper".equals(type.path()), "no dropper in ContainerType: " + type);
            no("dispenser".equals(type.path()), "no dispenser in ContainerType: " + type);
        }

        FakeInventory dropper = new FakeInventory(9, org.bukkit.event.inventory.InventoryType.DROPPER);
        FakeInventory hopper = new FakeInventory(5, org.bukkit.event.inventory.InventoryType.HOPPER);
        dropper.setItem(0, new ItemStack(Material.STONE, 64));

        // Even asking for a move explicitly can only ever transfer one item.
        eq(1, InventoryOps.moveOne(dropper, hopper), "dropper -> hopper moves exactly one item");
        eq(63, dropper.getItem(0).getAmount(), "dropper lost exactly one item");
        eq(1, hopper.total(Material.STONE), "hopper received exactly one item, not 8");

        // The same holds for the vanilla-transfer replacement path.
        eq(1, InventoryOps.moveOneSimilar(dropper, hopper, new ItemStack(Material.STONE, 1)),
                "dropper -> hopper similar move is also a single item");
        eq(62, dropper.getItem(0).getAmount(), "dropper lost one more item");
        eq(2, hopper.total(Material.STONE), "hopper total is 2 after two transfers");

        // And the amount is hard-capped in the math, whatever a caller requests.
        eq(1, TransferMath.moveAmount(8, 64, 64), "a request for 8 items yields 1");
        eq(1, TransferMath.moveAmount(64, 64, 64), "a request for a full stack yields 1");

        // A dropper is still a valid *destination* (a hopper may feed one),
        // and that insert is a single item too.
        FakeInventory feeder = new FakeInventory(5, org.bukkit.event.inventory.InventoryType.HOPPER);
        feeder.setItem(0, new ItemStack(Material.STONE, 10));
        FakeInventory target = new FakeInventory(9, org.bukkit.event.inventory.InventoryType.DROPPER);
        eq(1, InventoryOps.moveOne(feeder, target), "hopper -> dropper moves one item");
        eq(1, target.total(Material.STONE), "dropper received a single item");

        // The dropper's own pulse is vanilla's business: observing it must only
        // charge the cooldown, never move anything extra.
        TransferClock clock = new TransferClock();
        String hopperKey = TransferClock.blockKey("world", 0, 64, 0);
        clock.stamp(hopperKey);
        no(clock.isReady(hopperKey, 4), "hopper fed by a dropper waits out its cooldown");
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
        private final org.bukkit.event.inventory.InventoryType type;

        FakeInventory(int size) {
            this(size, org.bukkit.event.inventory.InventoryType.HOPPER);
        }

        FakeInventory(int size, org.bukkit.event.inventory.InventoryType type) {
            this.items = new ItemStack[size];
            this.type = type;
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
            return type;
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
