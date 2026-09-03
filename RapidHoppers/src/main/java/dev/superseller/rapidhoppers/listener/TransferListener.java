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
 * <p>When a hopper moves its single item, this listener immediately tops the
 * transfer up to {@code engine.items-per-transfer}. That covers containers the
 * scanning engine skipped (unloaded-adjacent chunks, exotic holders) and keeps
 * amounts consistent everywhere.</p>
 */
public final class TransferListener implements Listener {

    private final Settings settings;
    private final ThrottleMonitor throttle;
    private final Stats stats;

    public TransferListener(Settings settings, ThrottleMonitor throttle, Stats stats) {
        this.settings = settings;
        this.throttle = throttle;
        this.stats = stats;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        if (!settings.isEnabled() || !settings.isBoostVanillaTransfers() || throttle.isPaused()) {
            return;
        }
        Inventory source = event.getSource();
        Inventory destination = event.getDestination();
        if (source == null || destination == null) {
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
        if (moving == null || moving.getType().isAir()) {
            return;
        }
        int extra = settings.getItemsPerTransfer() - moving.getAmount();
        if (extra <= 0) {
            return;
        }
        int free = InventoryOps.freeSpaceFor(destination, moving) - moving.getAmount();
        int amount = TransferMath.moveAmount(extra, countSimilar(source, moving), free, moving.getMaxStackSize());
        if (amount <= 0) {
            return;
        }
        int moved = InventoryOps.moveOneStack(source, destination, amount);
        if (moved > 0) {
            stats.recordTransfer(moved);
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
            default -> settings.isContainerEnabled(ContainerType.HOPPER);
        };
    }

    private int countSimilar(Inventory inv, ItemStack probe) {
        int total = 0;
        for (ItemStack stack : inv.getStorageContents()) {
            if (stack != null && stack.isSimilar(probe)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Utility used by the GUI to close viewers safely. */
    public static void closeAll(Iterable<HumanEntity> viewers) {
        for (HumanEntity viewer : viewers) {
            viewer.closeInventory();
        }
    }
}
