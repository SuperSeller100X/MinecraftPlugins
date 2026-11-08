package dev.superseller.veltryx.shards;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.papermc.paper.event.entity.EntityLungeEvent;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import dev.superseller.veltryx.config.Messages;

/** Native spear Lunge, with reduced Lunge exhaustion, and dry trident Riptide. */
public final class ShardWeaponListener implements Listener {
    private static final long RIPTIDE_COOLDOWN_MS = 5_000L;
    private final Map<UUID, Long> nextRiptide = new ConcurrentHashMap<>();
    private final Map<UUID, LungeMarker> lungeMarkers = new ConcurrentHashMap<>();
    private record LungeMarker(long nanos, int level) { }
    private final ShardItems items;
    private final ShardCatalog catalog;
    private final Messages messages;

    public ShardWeaponListener(ShardItems items, ShardCatalog catalog, Messages messages) {
        this.items = items; this.catalog = catalog; this.messages = messages;
    }

    /** Paper fires this immediately before each native post-piercing Lunge effect. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLunge(EntityLungeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() != Material.matchMaterial("NETHERITE_SPEAR")
                || !"shard_spear".equals(items.itemId(held)) || !items.abilityEnabled(held)
                || !player.hasPermission("veltryx.shards")) return;
        int level = held.getEnchantmentLevel(Enchantment.LUNGE);
        if (level > 0 && event.getLungePower() > 0)
            lungeMarkers.put(player.getUniqueId(), new LungeMarker(System.nanoTime(), event.getLungePower()));
    }

    /** Correlate a matching recent Lunge and exhaustion amount; Paper does not attribute the effect to an item. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onExhaustion(EntityExhaustionEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || event.getExhaustionReason() != EntityExhaustionEvent.ExhaustionReason.ENCHANTMENT_EFFECT) return;
        LungeMarker marker = lungeMarkers.remove(player.getUniqueId());
        if (event.isCancelled() || marker == null || System.nanoTime() - marker.nanos() > 100_000_000L
                || Math.abs(event.getExhaustion() - 4.0f * marker.level()) > 0.001f) return;
        event.setExhaustion(event.getExhaustion() * 0.1f);
    }

    /** Vanilla handles wet Riptide unchanged; the dry extension is throttled. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() != Material.TRIDENT
                || !"shard_trident".equals(items.itemId(held))
                || held.getEnchantmentLevel(Enchantment.RIPTIDE) != 3
                || !items.abilityEnabled(held) || !player.hasPermission("veltryx.shards")) return;
        if (player.isInWater() || player.isInRain()) return;
        if (action == Action.RIGHT_CLICK_BLOCK && event.isCancelled()) return;
        if (held.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damage
                && held.getType().getMaxDurability() > 0
                && damage.getDamage() >= held.getType().getMaxDurability() - 1) return;
        long now = System.currentTimeMillis();
        long ready = nextRiptide.getOrDefault(player.getUniqueId(), 0L);
        if (now < ready) {
            messages.send(player, "shards.riptide-cooldown", Map.of("seconds", Long.toString((ready - now + 999) / 1000)));
            return;
        }
        nextRiptide.put(player.getUniqueId(), now + RIPTIDE_COOLDOWN_MS);
        player.damageItemStack(EquipmentSlot.HAND, 1);
        Vector direction = player.getLocation().getDirection().normalize();
        player.setVelocity(player.getVelocity().add(direction.multiply(3.0)));
        player.startRiptideAttack(20, 8.0f, held);
        player.playSound(player.getLocation(), "item.trident.riptide_3", 1.0f, 1.0f);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        nextRiptide.remove(event.getPlayer().getUniqueId());
        lungeMarkers.remove(event.getPlayer().getUniqueId());
    }
}
