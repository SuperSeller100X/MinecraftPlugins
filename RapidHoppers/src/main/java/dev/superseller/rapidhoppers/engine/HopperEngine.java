package dev.superseller.rapidhoppers.engine;

import java.util.ArrayList;
import java.util.List;

import dev.superseller.rapidhoppers.config.Settings;
import dev.superseller.rapidhoppers.config.Settings.ContainerType;
import dev.superseller.rapidhoppers.scheduler.PlatformScheduler;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Dropper;
import org.bukkit.block.Hopper;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The acceleration engine.
 *
 * <p>Every {@code engine.interval-ticks} ticks the engine walks the loaded
 * chunks of every enabled world, finds hoppers, hopper minecarts, storage
 * minecarts, droppers and dispensers and performs <em>additional</em> transfer
 * operations on top of whatever vanilla does. Vanilla logic is never disabled,
 * so redstone comparators, item sorters and all other contraptions keep
 * working &mdash; they simply move items far quicker.</p>
 *
 * <p>All world/block access is dispatched through
 * {@link PlatformScheduler#runAtLocation} so the engine is region-safe on
 * Folia and a plain inline call on Paper/Purpur.</p>
 */
public final class HopperEngine {

    private final JavaPlugin plugin;
    private final Settings settings;
    private final ThrottleMonitor throttle;
    private final Stats stats;

    private PlatformScheduler.Handle tickTask;
    private PlatformScheduler.Handle statsTask;
    private int currentInterval = -1;
    private volatile boolean running;
    private long tickCounter;

    public HopperEngine(JavaPlugin plugin, Settings settings, ThrottleMonitor throttle, Stats stats) {
        this.plugin = plugin;
        this.settings = settings;
        this.throttle = throttle;
        this.stats = stats;
    }

    // --- lifecycle ----------------------------------------------------------

    public void start() {
        stop();
        running = true;
        currentInterval = settings.getIntervalTicks();
        tickTask = PlatformScheduler.runTimer(this::tick, currentInterval, currentInterval);
        statsTask = PlatformScheduler.runTimer(stats::tickWindow, 20L, 20L);
    }

    public void stop() {
        running = false;
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (statsTask != null) {
            statsTask.cancel();
            statsTask = null;
        }
    }

    /** Restarts the loop when the configured interval changed. */
    public void refresh() {
        if (!running || currentInterval != settings.getIntervalTicks()) {
            start();
        }
    }

    public boolean isRunning() {
        return running;
    }

    // --- main loop ----------------------------------------------------------

    private void tick() {
        if (!settings.isEnabled() || throttle.isPaused()) {
            return;
        }
        tickCounter++;
        if (throttle.isThrottled() && tickCounter % Math.max(1, settings.getThrottleMultiplier()) != 0L) {
            return;
        }

        int budget = settings.getMaxTransfersPerTick() > 0
                ? settings.getMaxTransfersPerTick() : Integer.MAX_VALUE;
        int tracked = 0;

        for (World world : Bukkit.getWorlds()) {
            if (!settings.appliesToWorld(world.getName())) {
                continue;
            }
            int worldBudget = settings.getMaxContainersPerWorld() > 0
                    ? settings.getMaxContainersPerWorld() : Integer.MAX_VALUE;
            List<int[]> activeChunks = activeChunks(world);
            for (int[] coords : activeChunks) {
                if (budget <= 0 || worldBudget <= 0) {
                    break;
                }
                int handled = processChunk(world, coords[0], coords[1], Math.min(budget, worldBudget));
                tracked += handled;
                budget -= handled;
                worldBudget -= handled;
            }
        }
        stats.setTrackedContainers(tracked);
    }

    /** Loaded chunks that are close enough to a player to be worth processing. */
    private List<int[]> activeChunks(World world) {
        List<int[]> out = new ArrayList<>();
        int radius = settings.getPlayerActivityRadiusChunks();
        if (radius <= 0) {
            for (Chunk chunk : world.getLoadedChunks()) {
                out.add(new int[] {chunk.getX(), chunk.getZ()});
            }
            return out;
        }
        List<int[]> players = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            Location loc = player.getLocation();
            players.add(new int[] {loc.getBlockX() >> 4, loc.getBlockZ() >> 4});
        }
        if (players.isEmpty()) {
            return out;
        }
        for (Chunk chunk : world.getLoadedChunks()) {
            for (int[] origin : players) {
                if (TransferMath.chunkDistance(chunk.getX(), chunk.getZ(), origin[0], origin[1]) <= radius) {
                    out.add(new int[] {chunk.getX(), chunk.getZ()});
                    break;
                }
            }
        }
        return out;
    }

    /**
     * Processes one chunk. Returns the number of containers that were looked
     * at, which is also what the per-tick budget is charged for.
     */
    private int processChunk(World world, int chunkX, int chunkZ, int budget) {
        final int[] handled = {0};
        Location anchor = new Location(world, (chunkX << 4) + 8, world.getMinHeight() + 1, (chunkZ << 4) + 8);
        PlatformScheduler.runAtLocation(anchor, () -> {
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                return;
            }
            Chunk chunk = world.getChunkAt(chunkX, chunkZ);
            int limit = settings.getMaxContainersPerChunk() > 0
                    ? settings.getMaxContainersPerChunk() : Integer.MAX_VALUE;
            int used = 0;

            for (BlockState state : chunk.getTileEntities()) {
                if (used >= limit || handled[0] >= budget) {
                    break;
                }
                if (handleBlockState(state)) {
                    used++;
                    handled[0]++;
                }
            }
            for (Entity entity : chunk.getEntities()) {
                if (used >= limit || handled[0] >= budget) {
                    break;
                }
                if (handleEntity(entity)) {
                    used++;
                    handled[0]++;
                }
            }
        });
        return handled[0];
    }

    // --- container handlers -------------------------------------------------

    private boolean handleBlockState(BlockState state) {
        if (state instanceof Hopper hopper) {
            if (!settings.isContainerEnabled(ContainerType.HOPPER)) {
                return false;
            }
            tickHopper(hopper);
            return true;
        }
        if (state instanceof Dropper dropper) {
            if (!settings.isContainerEnabled(ContainerType.DROPPER)) {
                return false;
            }
            tickFacingPusher(dropper.getBlock(), dropper.getInventory(), settings.getDropperMultiplier());
            return true;
        }
        if (state instanceof Dispenser dispenser) {
            if (!settings.isContainerEnabled(ContainerType.DISPENSER)) {
                return false;
            }
            tickFacingPusher(dispenser.getBlock(), dispenser.getInventory(), settings.getDispenserMultiplier());
            return true;
        }
        return false;
    }

    private boolean handleEntity(Entity entity) {
        if (entity instanceof HopperMinecart cart) {
            if (!settings.isContainerEnabled(ContainerType.HOPPER_MINECART) || !cart.isEnabled()) {
                return false;
            }
            tickHopperMinecart(cart);
            return true;
        }
        if (entity instanceof StorageMinecart cart) {
            if (!settings.isContainerEnabled(ContainerType.CHEST_MINECART)) {
                return false;
            }
            tickStorageMinecart(cart);
            return true;
        }
        return false;
    }

    /** Extra pull/push/pickup work for a placed hopper. */
    private void tickHopper(Hopper hopper) {
        int amount = settings.getItemsPerTransfer();
        Inventory self = hopper.getInventory();
        Block block = hopper.getBlock();

        if (settings.isHopperPushToFacing() && InventoryOps.hasItems(self)) {
            Inventory target = facingInventory(block);
            if (target == null) {
                target = minecartInventoryAt(block.getRelative(0, -1, 0));
            }
            if (target != null) {
                record(InventoryOps.moveOneStack(self, target, amount));
            }
        }
        if (settings.isHopperPullFromAbove() && InventoryOps.hasSpace(self)) {
            Block above = block.getRelative(0, 1, 0);
            Inventory source = inventoryOf(above);
            if (source == null) {
                source = minecartInventoryAt(above);
            }
            if (source != null) {
                record(InventoryOps.moveOneStack(source, self, amount));
            }
        }
        if (settings.isHopperPickupItems() && InventoryOps.hasSpace(self)) {
            pickUpItems(block, self, amount);
        }
    }

    /** Droppers and dispensers pushing into the container they face. */
    private void tickFacingPusher(Block block, Inventory self, int multiplier) {
        if (multiplier > 1 && tickCounter % multiplier != 0L) {
            return;
        }
        if (!InventoryOps.hasItems(self)) {
            return;
        }
        Inventory target = facingInventory(block);
        if (target != null) {
            record(InventoryOps.moveOneStack(self, target, settings.getItemsPerTransfer()));
        }
    }

    private void tickHopperMinecart(HopperMinecart cart) {
        int amount = settings.getItemsPerTransfer();
        Inventory self = cart.getInventory();
        Block block = cart.getLocation().getBlock();

        if (settings.isMinecartPullFromAbove() && InventoryOps.hasSpace(self)) {
            Inventory above = inventoryOf(block.getRelative(0, 1, 0));
            if (above != null) {
                record(InventoryOps.moveOneStack(above, self, amount));
            }
        }
        if (settings.isMinecartPushToContainer() && InventoryOps.hasItems(self)) {
            Inventory below = inventoryOf(block.getRelative(0, -1, 0));
            if (below != null) {
                record(InventoryOps.moveOneStack(self, below, amount));
            }
        }
    }

    /** Storage minecarts sitting on a hopper are drained faster. */
    private void tickStorageMinecart(StorageMinecart cart) {
        Inventory self = cart.getInventory();
        if (!InventoryOps.hasItems(self)) {
            return;
        }
        Block below = cart.getLocation().getBlock().getRelative(0, -1, 0);
        Inventory target = inventoryOf(below);
        if (target != null && below.getState() instanceof Hopper) {
            record(InventoryOps.moveOneStack(self, target, settings.getItemsPerTransfer()));
        }
    }

    private void pickUpItems(Block block, Inventory self, int amount) {
        Location above = block.getLocation().add(0.5D, 1.0D, 0.5D);
        for (Entity entity : block.getWorld().getNearbyEntities(above, 0.5D, 0.6D, 0.5D)) {
            if (!(entity instanceof Item item) || item.isDead()) {
                continue;
            }
            ItemStack stack = item.getItemStack();
            int free = InventoryOps.freeSpaceFor(self, stack);
            int move = TransferMath.moveAmount(amount, stack.getAmount(), free, stack.getMaxStackSize());
            if (move <= 0) {
                continue;
            }
            ItemStack moving = stack.clone();
            moving.setAmount(move);
            if (!self.addItem(moving).isEmpty()) {
                continue;
            }
            int remaining = stack.getAmount() - move;
            if (remaining <= 0) {
                item.remove();
            } else {
                ItemStack left = stack.clone();
                left.setAmount(remaining);
                item.setItemStack(left);
            }
            record(move);
            return;
        }
    }

    // --- helpers ------------------------------------------------------------

    private Inventory facingInventory(Block block) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Directional directional)) {
            return null;
        }
        Block target = block.getRelative(directional.getFacing());
        Inventory inv = inventoryOf(target);
        return inv != null ? inv : minecartInventoryAt(target);
    }

    private Inventory inventoryOf(Block block) {
        if (block == null) {
            return null;
        }
        BlockState state = block.getState(false);
        if (state instanceof InventoryHolder holder) {
            return holder.getInventory();
        }
        return null;
    }

    private Inventory minecartInventoryAt(Block block) {
        if (block == null) {
            return null;
        }
        Location centre = block.getLocation().add(0.5D, 0.5D, 0.5D);
        for (Entity entity : block.getWorld().getNearbyEntities(centre, 0.6D, 0.6D, 0.6D)) {
            if (entity instanceof HopperMinecart || entity instanceof StorageMinecart) {
                return ((InventoryHolder) entity).getInventory();
            }
        }
        return null;
    }

    private void record(int moved) {
        if (moved > 0) {
            stats.recordTransfer(moved);
            if (settings.isDebug()) {
                plugin.getLogger().info("[debug] moved " + moved + " item(s)");
            }
        }
    }
}
