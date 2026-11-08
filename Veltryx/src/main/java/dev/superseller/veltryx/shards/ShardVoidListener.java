package dev.superseller.veltryx.shards;

import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import dev.superseller.veltryx.config.Messages;

/**
 * The Void Totem rescues its holder from a lethal void hit: the totem is
 * consumed, the player is teleported back to their last safe ground and
 * healed with Resistance and Slow Falling.
 */
public final class ShardVoidListener implements Listener {

    private final ShardItems items;
    private final ShardCatalog catalog;
    private final Messages messages;

    public ShardVoidListener(ShardItems items, ShardCatalog catalog, Messages messages) {
        this.items = items;
        this.catalog = catalog;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        if (!player.isDead() && player.getHealth() > 0
                && player.getHealth() - event.getFinalDamage() > 0) {
            return;   // not lethal - vanilla handles it
        }
        int slot = findTotem(player);
        if (slot < 0) {
            return;
        }
        event.setCancelled(true);
        player.getInventory().setItem(slot, new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR, 1));

        Location safe = safeLocation(player);
        if (safe != null) {
            player.teleport(safe);
        }
        player.setHealth(Math.max(1.0, player.getHealth()));
        // 20 ticks per second: 60s Resistance I + 60s Slow Falling I.
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60 * 20, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60 * 20, 0));
        messages.send(player, "shards.totem-saved", Map.of());
    }

    /** First inventory slot holding a Void Totem, or -1. */
    private int findTotem(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        if (contents == null) {
            return -1;
        }
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if ("void_totem".equals(items.itemId(stack))) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Best rescue spot: the world's top with some room, far above the void.
     * Falls back to the player's own spot raised up.
     */
    private Location safeLocation(Player player) {
        Location loc = player.getLocation();
        double y = Math.max(loc.getY() + 64.0, loc.getWorld().getMinHeight() + 128.0);
        return new Location(loc.getWorld(), loc.getX(), y, loc.getZ(), loc.getYaw(), loc.getPitch());
    }
}
