package dev.superseller.shardtools.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import dev.superseller.shardtools.ShardToolsPlugin;

/**
 * Keeps shard items intact: no renaming/combining in anvils and no
 * disenchanting in grindstones (both toggleable in the config).
 */
public final class AnvilListener implements Listener {

    private final ShardToolsPlugin plugin;

    public AnvilListener(ShardToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!plugin.settings().anvilProtect()) {
            return;
        }
        if (containsShardItem(event.getInventory()) || plugin.items().itemId(event.getResult()) != null) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (!plugin.settings().grindstoneProtect()) {
            return;
        }
        if (containsShardItem(event.getInventory()) || plugin.items().itemId(event.getResult()) != null) {
            event.setResult(null);
        }
    }

    private boolean containsShardItem(Inventory inventory) {
        for (ItemStack stack : inventory.getContents()) {
            if (plugin.items().itemId(stack) != null) {
                return true;
            }
        }
        return false;
    }
}
