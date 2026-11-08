package dev.superseller.veltryx.shards;

import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.superseller.veltryx.config.Messages;

/**
 * The wall-clock self-destruct: shard tools live 24 real-time hours and
 * then vanish, even while the holder is offline. A slow sweep over the
 * inventories of online players removes expired items and sends the
 * remaining-time warnings.
 */
public final class ShardExpiryService {

    /** Warning thresholds in minutes, descending (bit 0 = soonest sent first). */
    private static final long[] WARN_MINUTES = {60, 10};

    private final ShardItems items;
    private final ShardCatalog catalog;
    private final Messages messages;

    public ShardExpiryService(ShardItems items, ShardCatalog catalog, Messages messages) {
        this.items = items;
        this.catalog = catalog;
        this.messages = messages;
    }

    /** Sweeps one player's inventory: warns about and removes expired shard items. */
    public void sweep(Player player, long nowMs) {
        ItemStack[] contents = player.getInventory().getContents();
        if (contents == null) {
            return;
        }
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (items.itemId(stack) == null) {
                continue;
            }
            if (items.expired(stack, nowMs)) {
                player.getInventory().setItem(slot, new ItemStack(Material.AIR, 1));
                messages.send(player, "shards.expired", Map.of("item", displayName(stack)));
                continue;
            }
            warnIfNeeded(player, stack, nowMs);
        }
    }

    private void warnIfNeeded(Player player, ItemStack stack, long nowMs) {
        Long created = items.created(stack);
        Long lifetime = items.lifetime(stack);
        if (created == null || lifetime == null || lifetime <= 0L) {
            return;
        }
        long remainingMs = ExpiryMath.remaining(created, lifetime, nowMs);
        int bit = ExpiryMath.dueWarning(remainingMs, WARN_MINUTES, items.warnedBits(stack));
        if (bit < 0) {
            return;
        }
        items.markWarned(stack, bit);
        long minutes = Math.max(1L, remainingMs / 60_000L);
        messages.send(player, "shards.warn",
                Map.of("item", displayName(stack), "minutes", String.valueOf(minutes)));
    }

    private String displayName(ItemStack stack) {
        ShardCatalog.Entry entry = catalog.byId(items.itemId(stack));
        return entry == null ? "shard item" : entry.displayName();
    }

    /** Convenience: sweep every online player (used by the plugin timer). */
    public void sweepAll(Iterable<? extends Player> players, long nowMs) {
        for (Player player : players) {
            sweep(player, nowMs);
        }
    }
}
