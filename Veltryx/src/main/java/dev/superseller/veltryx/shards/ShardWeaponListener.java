package dev.superseller.veltryx.shards;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import dev.superseller.veltryx.config.Messages;

/**
 * The shard weapons:
 * <ul>
 *   <li><b>Shard Spear</b> - right-click to lunge forward. While lunging
 *       the hunger drain is 90% lower.</li>
 *   <li><b>Shard Trident</b> - Riptide works without water, with a 3
 *       second cooldown (in water the vanilla riptide still applies).</li>
 * </ul>
 */
public final class ShardWeaponListener implements Listener {

    private static final long LUNGE_COOLDOWN_MS = 2_000L;
    private static final long LUNGE_WINDOW_MS = 2_500L;
    private static final long RIPTIDE_COOLDOWN_MS = 3_000L;

    private final ShardItems items;
    private final ShardCatalog catalog;
    private final Messages messages;

    /** Next allowed lunge/riptide moment per player. */
    private final Map<UUID, Long> nextLunge = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextRiptide = new ConcurrentHashMap<>();
    /** Players currently lunging (reduced hunger drain). */
    private final Map<UUID, Long> lungingUntil = new ConcurrentHashMap<>();

    public ShardWeaponListener(ShardItems items, ShardCatalog catalog, Messages messages) {
        this.items = items;
        this.catalog = catalog;
        this.messages = messages;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;   // avoid the double fire of both hands
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        String id = items.itemId(held);
        if (id == null) {
            return;
        }
        ShardCatalog.Entry entry = catalog.byId(id);
        if (entry == null || !items.abilityEnabled(held) || !player.hasPermission("veltryx.shards")) {
            return;
        }
        if (entry.behavior() == Behavior.LUNGE_SPEAR) {
            lunge(player);
        } else if (entry.behavior() == Behavior.RIPTIDE_TRIDENT) {
            riptide(player);
        }
    }

    /** Shard Spear: a forward dash. Marks the player as lunging for 2.5s. */
    private void lunge(Player player) {
        long now = System.currentTimeMillis();
        if (now < nextLunge.getOrDefault(player.getUniqueId(), 0L)) {
            return;
        }
        nextLunge.put(player.getUniqueId(), now + LUNGE_COOLDOWN_MS);
        lungingUntil.put(player.getUniqueId(), now + LUNGE_WINDOW_MS);
        Vector direction = player.getLocation().getDirection().normalize();
        player.setVelocity(direction.multiply(1.7).add(new Vector(0.0, 0.35, 0.0)));
    }

    /** Shard Trident: riptide without water, 3s cooldown. */
    private void riptide(Player player) {
        if (player.isInWater() || player.getWorld().hasStorm()) {
            return;   // vanilla riptide handles water and rain
        }
        long now = System.currentTimeMillis();
        long readyAt = nextRiptide.getOrDefault(player.getUniqueId(), 0L);
        if (now < readyAt) {
            long seconds = (readyAt - now + 999) / 1000L;
            messages.send(player, "shards.riptide-cooldown", Map.of("seconds", String.valueOf(seconds)));
            return;
        }
        nextRiptide.put(player.getUniqueId(), now + RIPTIDE_COOLDOWN_MS);
        Vector direction = player.getLocation().getDirection().normalize();
        player.setVelocity(direction.multiply(2.2).add(new Vector(0.0, 0.3, 0.0)));
    }

    /** While lunging, 90% of the hunger drain is skipped. */
    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getFoodLevel() >= player.getFoodLevel()) {
            return;   // not a drain
        }
        long until = lungingUntil.getOrDefault(player.getUniqueId(), 0L);
        if (System.currentTimeMillis() >= until) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() < 0.90) {
            event.setCancelled(true);
        }
    }
}
