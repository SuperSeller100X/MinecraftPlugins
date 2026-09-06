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
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The acceleration engine.
 *
 * <p>RapidHoppers does exactly one thing: it makes hoppers run their vanilla
 * transfer <em>more often</em>. Every tick of this engine performs, at most,
 * the same single-item move a vanilla hopper would have performed on its own
 * 8-tick clock — pulling one item from the inventory directly above, or pushing
 * one item into the inventory the hopper faces.</p>
 *
 * <p>Three rules keep the behaviour identical to vanilla apart from speed:</p>
 * <ol>
 *   <li><b>One item per transfer.</b> Never a stack, never a configurable
 *       amount. Comparator readings, item sorters and filtered hoppers see the
 *       exact sequence of single-item moves they see in vanilla.</li>
 *   <li><b>A shared clock.</b> Vanilla's own transfers are stamped into the
 *       same {@link TransferClock} by {@code TransferListener}, so the plugin
 *       tops each hopper up to the configured rate rather than stacking its
 *       throughput on top of vanilla's.</li>
 *   <li><b>Only vanilla's own destinations.</b> A hopper pulls from the block
 *       directly above and pushes into the block it faces — nothing else. The
 *       engine never invents a destination, never reaches into a neighbouring
 *       container, and never touches a block vanilla would not insert into.</li>
 * </ol>
 *
 * <p>Droppers, dispensers, chest minecarts and hopper-minecart "push into the
 * block below" are deliberately not handled at all: none of them run on a
 * hopper clock in vanilla, so there is no rate to accelerate, and moving items
 * for them means moving items nobody asked to be moved.</p>
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
    private final TransferClock clock;

    private PlatformScheduler.Handle tickTask;
    private PlatformScheduler.Handle statsTask;
    private volatile boolean running;

    public HopperEngine(JavaPlugin plugin, Settings settings, ThrottleMonitor throttle,
                        Stats stats, TransferClock clock) {
        this.plugin = plugin;
        this.settings = settings;
        this.throttle = throttle;
        this.stats = stats;
        this.clock = clock;
    }

    // --- lifecycle ----------------------------------------------------------

    public void start() {
        stop();
        running = true;
        clock.clear();
        // The loop always runs every tick; the per-container cooldown, not the
        // loop period, is what enforces the configured interval. That keeps
        // hoppers evenly paced instead of moving in bursts.
        tickTask = PlatformScheduler.runTimer(this::tick, 1L, 1L);
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
        clock.clear();
    }

    /** Restarts the loop after a configuration change. */
    public void refresh() {
        if (!running) {
            start();
        }
    }

    public boolean isRunning() {
        return running;
    }

    /** Effective interval right now, including the throttle. */
    public int effectiveInterval() {
        return TransferMath.effectiveInterval(settings.getIntervalTicks(),
                throttle.isThrottled(), settings.getThrottleMultiplier());
    }

    // --- main loop ----------------------------------------------------------

    private void tick() {
        if (!settings.isEnabled() || throttle.isPaused()) {
            return;
        }
        clock.advance();

        int interval = effectiveInterval();
        if (interval >= Settings.VANILLA_INTERVAL_TICKS) {
            // Nothing to accelerate — vanilla is already this fast.
            stats.setTrackedContainers(0);
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
            for (int[] coords : activeChunks(world)) {
                if (budget <= 0 || worldBudget <= 0) {
                    break;
                }
                int handled = processChunk(world, coords[0], coords[1],
                        Math.min(budget, worldBudget), interval);
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
    private int processChunk(World world, int chunkX, int chunkZ, int budget, int interval) {
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

            if (settings.isContainerEnabled(ContainerType.HOPPER)) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (used >= limit || handled[0] >= budget) {
                        break;
                    }
                    if (handleBlockState(state, interval)) {
                        used++;
                        handled[0]++;
                    }
                }
            }
            if (settings.isContainerEnabled(ContainerType.HOPPER_MINECART)) {
                for (Entity entity : chunk.getEntities()) {
                    if (used >= limit || handled[0] >= budget) {
                        break;
                    }
                    if (handleEntity(entity, interval)) {
                        used++;
                        handled[0]++;
                    }
                }
            }
        });
        return handled[0];
    }

    // --- container handlers -------------------------------------------------

    private boolean handleBlockState(BlockState state, int interval) {
        if (!(state instanceof Hopper)) {
            return false;
        }
        // chunk.getTileEntities() returns snapshots on Paper. Moving out of a
        // snapshot and into a live destination is a dupe (source never shrinks).
        Block block = state.getBlock();
        if (block == null || !isChunkLoaded(block)) {
            return false;
        }
        BlockState live = block.getState(false);
        if (!(live instanceof Hopper hopper)) {
            return false;
        }
        return tickHopper(hopper, block, interval);
    }

    private boolean handleEntity(Entity entity, int interval) {
        if (!(entity instanceof HopperMinecart cart)) {
            return false;
        }
        if (!cart.isEnabled()) {
            return false;
        }
        return tickHopperMinecart(cart, interval);
    }

    /**
     * One accelerated vanilla tick for a placed hopper: push one item into the
     * faced inventory, or pull one item from the inventory above. Exactly what
     * vanilla does, only sooner.
     */
    private boolean tickHopper(Hopper hopper, Block block, int interval) {
        if (isRedstoneLocked(block)) {
            return false;
        }
        Inventory self = hopper.getInventory();
        if (!InventoryOps.isSimpleStorage(self)) {
            return false;
        }
        String key = TransferClock.blockKey(block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ());
        if (!clock.isReady(key, interval)) {
            return false;
        }

        // Vanilla order: push out first, then pull in.
        if (settings.isHopperPushToFacing() && InventoryOps.hasItems(self)) {
            Inventory target = facingInventory(block);
            if (target != null && !InventoryOps.sameInventory(self, target)
                    && record(key, InventoryOps.moveOne(self, target))) {
                return true;
            }
        }
        if (settings.isHopperPullFromAbove() && InventoryOps.hasSpace(self)) {
            Inventory source = inventoryOf(block.getRelative(0, 1, 0));
            if (source != null && !InventoryOps.sameInventory(self, source)
                    && record(key, InventoryOps.moveOne(source, self))) {
                return true;
            }
        }
        // Item-entity pickup is not accelerated: vanilla hoppers already scan
        // for dropped items every single tick, so there is no rate to improve.
        return true;
    }

    /**
     * Hopper minecarts pull one item from the inventory above them, which is
     * the only container interaction vanilla gives them. They are never made to
     * push into whatever block happens to be under the rail.
     */
    private boolean tickHopperMinecart(HopperMinecart cart, int interval) {
        if (!settings.isMinecartPullFromAbove()) {
            return false;
        }
        Inventory self = cart.getInventory();
        if (!InventoryOps.isSimpleStorage(self) || !InventoryOps.hasSpace(self)) {
            return false;
        }
        Location location = cart.getLocation();
        if (location == null || location.getWorld() == null) {
            return false;
        }
        Block block = location.getBlock();
        if (block == null || !isChunkLoaded(block)) {
            return false;
        }
        String key = TransferClock.entityKey(cart.getUniqueId());
        if (key == null || !clock.isReady(key, interval)) {
            return false;
        }
        Inventory above = inventoryOf(block.getRelative(0, 1, 0));
        if (above != null && !InventoryOps.sameInventory(self, above)) {
            record(key, InventoryOps.moveOne(above, self));
        }
        return true;
    }

    // --- helpers ------------------------------------------------------------

    /** The inventory a hopper faces, or null when there is none. */
    private Inventory facingInventory(Block block) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Directional directional)) {
            return null;
        }
        BlockFace facing = directional.getFacing();
        if (facing == null) {
            return null;
        }
        return inventoryOf(block.getRelative(facing));
    }

    /**
     * Live inventory of a simple storage container in a loaded chunk.
     * Furnaces, brewers, crafters and the like are skipped so {@code addItem}
     * cannot stuff the result slot or steal fuel — those keep pure vanilla
     * behaviour and speed.
     */
    private Inventory inventoryOf(Block block) {
        if (block == null || !isChunkLoaded(block)) {
            return null;
        }
        BlockState state = block.getState(false);
        if (state instanceof InventoryHolder holder) {
            Inventory inv = holder.getInventory();
            if (InventoryOps.isSimpleStorage(inv)) {
                return inv;
            }
        }
        return null;
    }

    /** Vanilla hoppers with HopperBlock.ENABLED = false do not transfer. */
    private boolean isRedstoneLocked(Block block) {
        if (block == null) {
            return false;
        }
        BlockData data = block.getBlockData();
        return data instanceof org.bukkit.block.data.type.Hopper hopperData && !hopperData.isEnabled();
    }

    private boolean isChunkLoaded(Block block) {
        if (block == null) {
            return false;
        }
        World world = block.getWorld();
        return world != null && world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
    }

    /** Records a transfer against the shared clock. Returns true if one moved. */
    private boolean record(String key, int moved) {
        if (moved <= 0) {
            return false;
        }
        clock.stamp(key);
        stats.recordTransfer(moved);
        if (settings.isDebug()) {
            plugin.getLogger().info("[debug] " + key + " moved " + moved + " item");
        }
        return true;
    }
}
