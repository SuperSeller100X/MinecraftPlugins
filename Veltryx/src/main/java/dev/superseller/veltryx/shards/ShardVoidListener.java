package dev.superseller.veltryx.shards;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.util.PlatformScheduler;

/** The ShardTools-style lethal void rescue, with entity-safe async teleport. */
public final class ShardVoidListener implements Listener {
    private final ShardItems items;
    private final Messages messages;
    private final Map<UUID, Location> lastSafe = new ConcurrentHashMap<>();
    private static final class Rescue { volatile long until = Long.MAX_VALUE; }
    private final Map<UUID, Rescue> pending = new ConcurrentHashMap<>();
    private final Map<UUID, java.util.concurrent.ConcurrentLinkedQueue<ItemStack>> refunds = new ConcurrentHashMap<>();
    public ShardVoidListener(ShardItems items, ShardCatalog catalog, Messages messages) {
        this.items = items; this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo(), from = event.getFrom();
        if (to == null || from == null || to.getWorld() == null) return;
        if (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY()
                && to.getBlockZ() == from.getBlockZ()) return;
        Player player = event.getPlayer();
        if (player.isDead() || player.isFlying() || player.isGliding() || !player.isOnGround()
                || to.getY() <= to.getWorld().getMinHeight() + 3) return;
        if (to.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) lastSafe.put(player.getUniqueId(), to.clone());
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.VOID
                || player.isDead()) return;
        UUID id = player.getUniqueId(); long now = System.currentTimeMillis();
        Rescue active = pending.get(id);
        if (active != null && now < active.until) { event.setCancelled(true); return; }
        // Paper's final damage already includes the ABSORPTION modifier. Adding
        // absorption to health here would count it twice and miss lethal hits.
        if (event.getFinalDamage() < player.getHealth()) return;
        int slot = findTotem(player); if (slot < 0) return;
        ItemStack totem = player.getInventory().getItem(slot);
        ItemStack refund = totem.clone(); refund.setAmount(1);
        if (totem.getAmount() > 1) { totem.setAmount(totem.getAmount() - 1); player.getInventory().setItem(slot, totem); }
        else player.getInventory().setItem(slot, null);
        // Keep the rescue locked until the async teleport resolves; a slow
        // destination must not consume additional totems on later void ticks.
        event.setCancelled(true);
        Rescue rescue = new Rescue(); pending.put(id, rescue);
        Location dest = lastSafe.get(id);
        if (dest == null || !dest.getWorld().equals(player.getWorld())) dest = player.getWorld().getSpawnLocation();
        player.setFallDistance(0);
        var health = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(health == null ? 20.0 : health.getValue());
        player.setAbsorptionAmount(0);
        clearHarmful(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 45 * 20, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30 * 20, 0));
        if (dest == null) { failedRescue(player, id, refund, rescue); return; }
        try {
            player.teleportAsync(dest.clone()).whenComplete((success, error) -> {
                if (error != null || !Boolean.TRUE.equals(success)) { failedRescue(player, id, refund, rescue); return; }
                // The player may have quit while the teleport was pending.
                rescue.until = System.currentTimeMillis() + 5_000L;
                PlatformScheduler.runForPlayer(player, () -> {
                    player.playEffect(EntityEffect.TOTEM_RESURRECT);
                    player.playSound(player.getLocation(), "item.totem.use", 1.0f, 1.0f);
                    Location p = player.getLocation();
                    player.getWorld().spawnParticle(Particle.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.4, 0.4, 0.01);
                    messages.send(player, "shards.totem-saved", Map.of());
                });
            });
        } catch (RuntimeException failure) {
            failedRescue(player, id, refund, rescue);
        }
    }
    private void failedRescue(Player player, UUID id, ItemStack item, Rescue rescue) {
        pending.remove(id, rescue);
        refunds.compute(id, (ignored, queue) -> {
            if (queue == null) queue = new java.util.concurrent.ConcurrentLinkedQueue<>();
            queue.add(item); return queue;
        });
        PlatformScheduler.runForPlayer(player, () -> deliverRefunds(player));
    }
    private void deliverRefunds(Player player) {
        var queue = refunds.remove(player.getUniqueId());
        if (queue == null) return;
        ItemStack item;
        while ((item = queue.poll()) != null) {
            refund(player, item);
            player.sendMessage(dev.superseller.veltryx.util.Txt.legacy("§cVoid rescue teleport failed; your totem was returned."));
        }
    }
    @EventHandler public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        PlatformScheduler.runForPlayer(event.getPlayer(), () -> deliverRefunds(event.getPlayer()));
    }
    private void refund(Player player, ItemStack item) {
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            var dropped = player.getWorld().dropItemNaturally(player.getLocation(), left);
            if (dropped != null) { dropped.setOwner(player.getUniqueId()); dropped.setUnlimitedLifetime(true); }
        }
    }
    private void clearHarmful(Player player) {
        String[] harmful = {"poison", "wither", "slowness", "weakness", "hunger", "levitation", "blindness",
                "nausea", "mining_fatigue", "bad_luck", "darkness", "wind_charged", "weaving", "oozing", "infested"};
        for (String key : harmful) {
            PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(key));
            if (type != null) player.removePotionEffect(type);
        }
    }
    private int findTotem(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        if (contents == null) return -1;
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && stack.getType() == Material.TOTEM_OF_UNDYING && "void_totem".equals(items.itemId(stack))) return i;
        }
        return -1;
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        lastSafe.remove(event.getPlayer().getUniqueId()); pending.remove(event.getPlayer().getUniqueId());
    }
}
