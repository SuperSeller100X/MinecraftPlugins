package dev.superseller.rapidhoppers.listener;

import dev.superseller.rapidhoppers.config.Settings;
import dev.superseller.rapidhoppers.config.Settings.ContainerType;
import dev.superseller.rapidhoppers.engine.InventoryOps;
import dev.superseller.rapidhoppers.engine.Stats;
import dev.superseller.rapidhoppers.engine.ThrottleMonitor;
import dev.superseller.rapidhoppers.engine.TransferMath;

import org.bukkit.Location;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Boosts the transfers vanilla itself performs.
 *
 * <p>When a hopper (or dropper / dispenser inserting into a container) would
 * move a single item, this listener <em>replaces</em> that transfer with one
 * of {@code engine.items-per-transfer} items of the same type.</p>
 *
 * <p>The vanilla event is cancelled and the plugin performs the whole move.
 * Adding extra items on top of an uncancelled event is the classic hopper
 * dupe: the plugin takes the last item, then vanilla still deposits its
 * clone into the destination.</p>
 */
public final class TransferListener implements Listener {

    private final Settings settings;
    private final ThrottleMonitor throttle;
    private final Stats stats;

    /** Prevents re-entrant move events from stacking transfers. */
    private final ThreadLocal<Boolean> replacing = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public TransferListener(Settings settings, ThrottleMonitor throttle, Stats stats) {
        this.settings = settings;
        this.throttle = throttle;
        this.stats = stats;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        if (Boolean.TRUE.equals(replacing.get())) {
            return;
        }
        if (!settings.isEnabled() || !settings.isBoostVanillaTransfers() || throttle.isPaused()) {
            return;
        }
        Inventory source = event.getSource();
        Inventory destination = event.getDestination();
        if (source == null || destination == null || InventoryOps.sameInventory(source, destination)) {
            return;
        }
        if (!InventoryOps.isSimpleStorage(source) || !InventoryOps.isSimpleStorage(destination)) {
            return;
        }
        if (!typeEnabled(source.getType()) && !typeEnabled(destination.getType())) {
            return;
        }
        Location location = destination.getLocation() != null ? destination.getLocation() : source.getLocation();
        if (location != null && location.getWorld() != null
                && !settings.appliesToWorld(location.getWorld().getName())) {
            return;
        }
        ItemStack moving = event.getItem();
        if (moving == null || moving.getType().isAir() || moving.getAmount() <= 0) {
            return;
        }
        int wanted = TransferMath.replaceAmount(
                settings.getItemsPerTransfer(),
                moving.getAmount(),
                InventoryOps.countSimilar(source, moving),
                InventoryOps.freeSpaceFor(destination, moving),
                moving.getMaxStackSize());
        if (wanted <= 0) {
            return;
        }
        // Replacing vanilla's 1-item move with the same 1 item is a no-op.
        if (wanted <= moving.getAmount()) {
            return;
        }

        replacing.set(Boolean.TRUE);
        try {
            int moved = InventoryOps.moveSimilar(source, destination, moving, wanted);
            if (moved > 0) {
                // Vanilla still holds a clone it will deposit unless we cancel.
                event.setCancelled(true);
                stats.recordTransfer(moved);
            }
        } finally {
            replacing.set(Boolean.FALSE);
        }
    }

    private boolean typeEnabled(InventoryType type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case HOPPER -> settings.isContainerEnabled(ContainerType.HOPPER)
                    || settings.isContainerEnabled(ContainerType.HOPPER_MINECART);
            case DROPPER -> settings.isContainerEnabled(ContainerType.DROPPER);
            case DISPENSER -> settings.isContainerEnabled(ContainerType.DISPENSER);
            default -> false;
        };
    }

    /** Utility used by the GUI to close viewers safely. */
    public static void closeAll(Iterable<HumanEntity> viewers) {
        for (HumanEntity viewer : viewers) {
            viewer.closeInventory();
        }
    }
}
