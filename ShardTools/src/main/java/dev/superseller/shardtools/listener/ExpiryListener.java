package dev.superseller.shardtools.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;

import dev.superseller.shardtools.ShardToolsPlugin;
import dev.superseller.shardtools.item.ShardCatalog;
import dev.superseller.shardtools.scheduler.PlatformScheduler;

/**
 * Self-destruct bookkeeping for offline periods: expired items are removed
 * when their holder joins, when they are picked up, and when a container
 * holding them is opened.
 */
public final class ExpiryListener implements Listener {

    private final ShardToolsPlugin plugin;

    public ExpiryListener(ShardToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlatformScheduler.runEntityLater(event.getPlayer(), () -> plugin.sweep().scan(event.getPlayer()), 40L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        long now = System.currentTimeMillis();
        if (plugin.items().expired(event.getItem().getItemStack(), now)) {
            String id = plugin.items().itemId(event.getItem().getItemStack());
            ShardCatalog.Entry entry = id == null ? null : plugin.catalog().byId(id);
            event.setCancelled(true);
            event.getItem().remove();
            if (entry != null) {
                plugin.messages().send(player, "expire.destroyed", "%item%", entry.displayName());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!plugin.settings().scanOpenedInventories()) {
            return;
        }
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        int removed = plugin.sweep().scanInventory(event.getInventory(), player);
        Inventory bottom = event.getView().getBottomInventory();
        if (bottom != event.getInventory()) {
            removed += plugin.sweep().scanInventory(bottom, player);
        }
        if (removed > 0) {
            player.updateInventory();
        }
    }
}
