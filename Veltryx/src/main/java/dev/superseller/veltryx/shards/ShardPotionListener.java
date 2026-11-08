package dev.superseller.veltryx.shards;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;

/**
 * Drinking a Shard Potion of Haste grants Haste II for one hour (or the
 * bought extra time) - a portable beacon.
 */
public final class ShardPotionListener implements Listener {

    private final ShardItems items;
    private final ShardCatalog catalog;
    private final Messages messages;
    private final VeltryxConfig config;

    public ShardPotionListener(ShardItems items, ShardCatalog catalog,
                               Messages messages, VeltryxConfig config) {
        this.items = items;
        this.catalog = catalog;
        this.messages = messages;
        this.config = config;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack stack = event.getItem();
        String id = items.itemId(stack);
        if (id == null) {
            return;
        }
        ShardCatalog.Entry entry = catalog.byId(id);
        if (entry == null || entry.behavior() != Behavior.HASTE_POTION) {
            return;
        }
        long minutes = config.shardHasteMinutes();
        Long custom = items.effectMinutes(stack);
        if (custom != null) {
            minutes = custom;
        }
        Player player = event.getPlayer();
        // Haste II: amplifier 1 = level II. 20 ticks per second.
        int ticks = (int) Math.min(Integer.MAX_VALUE, minutes * 60L * 20L);
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, ticks, 1));
        messages.send(player, "shards.potion", Map.of("minutes", String.valueOf(minutes)));
    }
}
