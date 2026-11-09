package dev.superseller.veltryx.shards;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.event.entity.EntityLungeEvent;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.Statistic;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import dev.superseller.veltryx.config.Messages;

/** Native spear Lunge, with reduced Lunge exhaustion, and dry trident Riptide. */
public final class ShardWeaponListener implements Listener {
    private static final long RIPTIDE_COOLDOWN_MS = 5_000L;
    private static final int RIPTIDE_CHARGE_TICKS = 10;
    // Player data survives reconnects, so relogging cannot bypass a dry cooldown.
    private static final NamespacedKey DRY_RIPTIDE_READY =
            Objects.requireNonNull(NamespacedKey.fromString("veltryx:dry_riptide_ready"));
    private final Set<UUID> launching = ConcurrentHashMap.newKeySet();
    private final Map<UUID, LungeMarker> lungeMarkers = new ConcurrentHashMap<>();
    private record LungeMarker(long nanos, int level) { }
    private final ShardItems items;
    private final Messages messages;

    public ShardWeaponListener(ShardItems items, ShardCatalog catalog, Messages messages) {
        this.items = items; this.messages = messages;
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

    /**
     * A vanilla client rejects the dry main-hand use locally and may also send
     * an offhand use for the same click. While the main hand is already charging
     * the shard trident, suppress that incidental second action as vanilla does.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onOffhandWhileCharging(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.OFF_HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (wet(player) || !player.hasActiveItem() || player.getActiveItemHand() != EquipmentSlot.HAND
                || !eligible(player, player.getActiveItem())) return;
        event.setCancelled(true);
    }

    /**
     * Start the normal active-item charge while dry. Do not cancel or alter the
     * interaction: wet Riptide and other plugins' item-use decisions stay native.
     * MONITOR observes the final item-use result; air interactions are normally
     * marked cancelled for the *block* even when item use is allowed.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.useItemInHand() == Event.Result.DENY) return;
        Player player = event.getPlayer();
        if (wet(player) || player.isDead() || player.getGameMode() == GameMode.SPECTATOR
                || player.hasActiveItem()) return;
        ItemStack held = inHand(player, hand);
        ItemStack clickedItem = event.getItem();
        if (!eligible(player, held) || clickedItem == null || !held.isSimilar(clickedItem)
                || wouldBreak(held) || player.hasCooldown(held)) return;
        if (action == Action.RIGHT_CLICK_BLOCK && !allowsItemUseOnBlock(event, player)) return;
        // Starting is deliberately not gated by the dry cooldown: the player
        // may enter water before releasing, in which case vanilla must work.
        player.startUsingItem(hand);
    }

    /**
     * Paper calls this before the native item release. If the player is wet,
     * leave everything to vanilla; if dry, only a fully charged, still-held
     * Shard Trident may use the extension. MONITOR observes prior stop-listener
     * changes before native release. Interruptions do not fire a launch.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStopUsing(PlayerStopUsingItemEvent event) {
        Player player = event.getPlayer();
        if (event.getTicksHeldFor() < RIPTIDE_CHARGE_TICKS || wet(player)
                || player.isDead() || player.isInsideVehicle()
                || player.getGameMode() == GameMode.SPECTATOR || !player.hasActiveItem()) return;
        EquipmentSlot hand = player.getActiveItemHand();
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) return;
        ItemStack held = inHand(player, hand);
        if (!sameEligibleItem(player, held, event.getItem()) || wouldBreak(held)) return;
        UUID id = player.getUniqueId();
        // A listener may synchronously cause another stop event from the
        // Riptide event. Do not launch twice or recurse indefinitely.
        if (!launching.add(id)) return;
        try {
            if (coolingDown(player, System.currentTimeMillis())) return;
            int level = held.getEnchantmentLevel(Enchantment.RIPTIDE);
            // Vanilla Riptide strength is 1.5 + 0.75 per level above I.
            Vector impulse = player.getLocation().getDirection().normalize().multiply(1.5 + 0.75 * (level - 1));
            PlayerRiptideEvent riptide = new PlayerRiptideEvent(player, held, impulse.clone());
            if (!riptide.callEvent()) return;

            // Event handlers can change the player's state or item. In
            // particular, becoming wet must fall through to native Riptide.
            ItemStack current = inHand(player, hand);
            if (wet(player) || player.isDead() || player.isInsideVehicle()
                    || player.getGameMode() == GameMode.SPECTATOR || !player.hasActiveItem()
                    || player.getActiveItemHand() != hand || !held.isSimilar(current)
                    || !sameEligibleItem(player, current, event.getItem()) || wouldBreak(current)) return;
            long now = System.currentTimeMillis();
            if (coolingDown(player, now)) return;
            // Paper's damageItemStack is forced even in creative; vanilla is not.
            if (player.getGameMode() != GameMode.CREATIVE) player.damageItemStack(hand, 1);
            current = inHand(player, hand);
            // Item-damage listeners can also replace the item or interrupt use.
            if (wet(player) || player.isDead() || player.isInsideVehicle()
                    || player.getGameMode() == GameMode.SPECTATOR || !player.hasActiveItem()
                    || player.getActiveItemHand() != hand || !sameEligibleItem(player, current, event.getItem())) return;
            player.incrementStatistic(Statistic.USE_ITEM, Material.TRIDENT);
            Vector velocity = player.getVelocity().add(impulse);
            // Native Riptide collision-moves grounded players upward. Paper has
            // no equivalent public movement API, so use a small safe lift.
            if (player.isOnGround() && impulse.getY() >= 0.0) velocity.setY(Math.max(0.2, velocity.getY()));
            player.setVelocity(velocity);
            player.startRiptideAttack(20, 8.0f, current);
            player.getPersistentDataContainer().set(DRY_RIPTIDE_READY, PersistentDataType.LONG,
                    System.currentTimeMillis() + RIPTIDE_COOLDOWN_MS);
            player.getWorld().playSound(player.getLocation(), "item.trident.riptide_" + Math.min(3, level),
                    SoundCategory.PLAYERS, 1.0f, 1.0f);
        } finally {
            launching.remove(id);
        }
    }

    private boolean coolingDown(Player player, long now) {
        Long ready = player.getPersistentDataContainer().get(DRY_RIPTIDE_READY, PersistentDataType.LONG);
        if (ready == null || now >= ready) return false;
        messages.send(player, "shards.riptide-cooldown",
                Map.of("seconds", Long.toString((ready - now + 999L) / 1000L)));
        return true;
    }

    private boolean sameEligibleItem(Player player, ItemStack held, ItemStack released) {
        return eligible(player, held) && released != null && held.isSimilar(released)
                && held.isSimilar(player.getActiveItem());
    }

    private boolean eligible(Player player, ItemStack held) {
        return held != null && held.getType() == Material.TRIDENT && held.getAmount() > 0
                && "shard_trident".equals(items.itemId(held))
                && held.getEnchantmentLevel(Enchantment.RIPTIDE) > 0
                && items.abilityEnabled(held) && player.hasPermission("veltryx.shards");
    }

    private static boolean wet(Player player) { return player.isInWater() || player.isInRain(); }

    private static ItemStack inHand(Player player, EquipmentSlot hand) {
        return hand == EquipmentSlot.HAND ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
    }

    /** Match the native nextDamageWillBreak check, including custom components. */
    private static boolean wouldBreak(ItemStack held) {
        Integer max = held.getData(DataComponentTypes.MAX_DAMAGE);
        Integer damage = held.getData(DataComponentTypes.DAMAGE);
        return max != null && damage != null && !held.hasData(DataComponentTypes.UNBREAKABLE)
                && damage >= max - 1;
    }

    @SuppressWarnings("deprecation") // A conservative approximation of whether a block consumes right-click.
    private static boolean allowsItemUseOnBlock(PlayerInteractEvent event, Player player) {
        if (event.getClickedBlock() == null) return false;
        if (event.useInteractedBlock() == Event.Result.DENY)
            return event.useItemInHand() == Event.Result.ALLOW;
        return player.isSneaking() || !event.getClickedBlock().getType().isInteractable();
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        lungeMarkers.remove(event.getPlayer().getUniqueId());
    }
}
