package dev.superseller.shardtools.listener;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

import dev.superseller.shardtools.ShardToolsPlugin;

/**
 * Amethyst chime + purple particle ring whenever a shard item (armor from
 * the shard shop) is equipped - the DonutSMP amethyst feel.
 */
public final class EquipListener implements Listener {

    private final ShardToolsPlugin plugin;

    public EquipListener(ShardToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        ItemStack newItem = event.getNewItem();
        if (newItem == null) {
            return;
        }
        if (plugin.items().itemId(newItem) != null) {
            plugin.effects().equipEffect(event.getPlayer());
        }
    }

    /**
     * "Equipping" a shard tool - pulling it into your hand - plays the
     * amethyst chime and a purple particle ring, just like on DonutSMP.
     */
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        ItemStack newItem = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (newItem == null) {
            return;
        }
        if (plugin.items().itemId(newItem) != null) {
            plugin.effects().equipEffect(event.getPlayer());
        }
    }
}
