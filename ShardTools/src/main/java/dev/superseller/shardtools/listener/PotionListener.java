package dev.superseller.shardtools.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;

import dev.superseller.shardtools.ShardToolsPlugin;
import dev.superseller.shardtools.item.Behavior;
import dev.superseller.shardtools.item.ShardCatalog;
import dev.superseller.shardtools.util.TimeWords;

/**
 * Shard Potion of Haste - DonutSMP's "portable beacon":
 * grants Haste for the configured duration (default 1 hour) when drunk.
 */
public final class PotionListener implements Listener {

    private final ShardToolsPlugin plugin;

    public PotionListener(ShardToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        String id = plugin.items().itemId(event.getItem());
        if (id == null) {
            return;
        }
        ShardCatalog.Entry entry = plugin.catalog().byId(id);
        if (entry == null || entry.behavior() != Behavior.HASTE_POTION) {
            return;
        }
        // Potions bought with extra time carry their own effect duration.
        Long overrideMinutes = plugin.items().effectMinutes(event.getItem());
        long effectMs = overrideMinutes != null
                ? overrideMinutes * 60_000L
                : plugin.settings().hasteDurationHours() * 3_600_000L;
        long ticks = effectMs / 1000L * 20L;
        org.bukkit.potion.PotionEffect haste = new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.HASTE, (int) Math.min(Integer.MAX_VALUE, ticks),
                plugin.settings().hasteAmplifier());
        player.addPotionEffect(haste);
        plugin.messages().send(player, "potion.haste",
                "%time%", TimeWords.format(effectMs));
        plugin.effects().equipEffect(player);
    }
}
