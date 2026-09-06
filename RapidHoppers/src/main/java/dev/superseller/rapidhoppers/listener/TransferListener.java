package dev.superseller.rapidhoppers.listener;

import dev.superseller.rapidhoppers.engine.InventoryOps;
import dev.superseller.rapidhoppers.engine.Stats;
import dev.superseller.rapidhoppers.engine.TransferClock;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Keeps the engine's clock in sync with the transfers vanilla performs itself.
 *
 * <p>This listener <em>observes only</em>. It never cancels an event, never
 * changes an amount and never moves an item. Its single job is to stamp the
 * source container's {@link TransferClock} entry whenever vanilla moves
 * something, so the engine knows that hopper has just transferred and must wait
 * out its cooldown before RapidHoppers gives it another turn.</p>
 *
 * <p>Without this, the plugin's transfers would be added <em>on top of</em>
 * vanilla's instead of replacing them: hoppers would exceed the configured
 * rate, move items in bursts, and race vanilla for the same item — the
 * behaviour that made items land in containers nobody intended to fill.</p>
 */
public final class TransferListener implements Listener {

    private final Stats stats;
    private final TransferClock clock;

    public TransferListener(Stats stats, TransferClock clock) {
        this.stats = stats;
        this.clock = clock;
    }

    /** Vanilla moved an item between two containers — charge the cooldown. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        // The "initiator" is the hopper doing the work: it pulls from a chest
        // above, or pushes into the container it faces. Either way it is the
        // container whose clock must be stamped.
        Inventory initiator = event.getInitiator();
        String key = keyOf(initiator != null ? initiator : event.getSource());
        if (key != null) {
            clock.stamp(key);
            stats.recordTransfer(1);
        }
    }

    /** Vanilla hopper picked a dropped item up — that is a transfer too. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(InventoryPickupItemEvent event) {
        String key = keyOf(event.getInventory());
        if (key != null) {
            clock.stamp(key);
        }
    }

    /** Clock key for whichever container backs this inventory. */
    private String keyOf(Inventory inventory) {
        if (inventory == null || !InventoryOps.isSimpleStorage(inventory)) {
            return null;
        }
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof Entity entity) {
            return TransferClock.entityKey(entity.getUniqueId());
        }
        Location location = inventory.getLocation();
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return TransferClock.blockKey(location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** Utility used by the GUI to close viewers safely. */
    public static void closeAll(Iterable<HumanEntity> viewers) {
        for (HumanEntity viewer : viewers) {
            viewer.closeInventory();
        }
    }
}
