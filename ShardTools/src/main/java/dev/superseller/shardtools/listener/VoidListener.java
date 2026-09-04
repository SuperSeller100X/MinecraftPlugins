package dev.superseller.shardtools.listener;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Attribute;
import org.bukkit.AttributeInstance;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import dev.superseller.shardtools.ShardToolsPlugin;
import dev.superseller.shardtools.config.VoidTotemSettings.ConsumeFrom;
import dev.superseller.shardtools.config.VoidTotemSettings.RescueMode;
import dev.superseller.shardtools.item.Behavior;
import dev.superseller.shardtools.item.ShardCatalog;
import dev.superseller.shardtools.scheduler.PlatformScheduler;

/**
 * The Void Totem rescue. When a player carrying a Void Totem (shard shop,
 * behavior: VOID_TOTEM) is about to die in the void, one totem is consumed
 * and the player is pulled back to safety: teleport to the last grounded
 * spot (or spawn / configured coords), full heal, harmful effects cleared,
 * Resistance + Slow Falling on landing, plus the vanilla totem animation,
 * sound and a particle burst. All rescue work runs through the plugin's
 * Folia-safe scheduler, so it is equally at home on Paper, Purpur and Folia.
 *
 * Only Void Totems from the shard shop are consumed - vanilla totems of
 * undying (and their normal pop-up behaviour) are never touched.
 */
@SuppressWarnings("deprecation")
public final class VoidListener implements Listener {

    private final ShardToolsPlugin plugin;
    private final Map<UUID, Location> lastSafe = new HashMap<>();
    private final Map<UUID, Long> pending = new HashMap<>();

    public VoidListener(ShardToolsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Remembers the last grounded, above-void spot each player stood on. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null) {
            return;
        }
        if (to.getBlockX() == from.getBlockX()
                && to.getBlockY() == from.getBlockY()
                && to.getBlockZ() == from.getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isDead() || player.isFlying() || player.isGliding()) {
            return;
        }
        World world = to.getWorld();
        if (to.getY() <= world.getMinHeight() + 3) {
            return;
        }
        if (!player.isOnGround()) {
            return;
        }
        if (!to.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
            return;
        }
        lastSafe.put(player.getUniqueId(), to.clone());
    }

    /** The rescue trigger: a lethal void hit while carrying a Void Totem. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        if (player.isDead()) {
            return;
        }
        if (!plugin.voidTotem().enabled()) {
            return;
        }
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - pending.getOrDefault(id, 0L) < plugin.voidTotem().cooldownSeconds() * 1000L) {
            // A rescue is already under way - keep stray void ticks from
            // killing the player mid-teleport without burning a second totem.
            event.setCancelled(true);
            return;
        }
        // Only act when this hit would actually be lethal (health + absorption).
        double effective = player.getHealth() + player.getAbsorptionAmount();
        if (event.getFinalDamage() < effective) {
            return;
        }
        if (!hasVoidTotem(player)) {
            return;
        }
        // Consume one totem, cancel the lethal hit, and run the rescue on
        // the player's own region thread (Folia-safe, next tick on Paper).
        if (!removeOneVoidTotem(player)) {
            return;
        }
        event.setCancelled(true);
        pending.put(id, now);
        PlatformScheduler.runEntity(player, () -> doRescue(player, id));
    }

    private void doRescue(Player player, UUID id) {
        if (!player.isOnline() || player.isDead()) {
            pending.remove(id);
            return;
        }
        Location dest = computeDestination(player);

        if (dest != null) {
            player.teleport(dest);
        }
        player.setFallDistance(0);
        fullHeal(player);
        if (plugin.voidTotem().clearHarmful()) {
            clearHarmful(player);
        }
        applyEffect(player, "resistance", plugin.voidTotem().resistanceSeconds());
        applyEffect(player, "slow_falling", plugin.voidTotem().slowFallingSeconds());
        if (plugin.voidTotem().animation()) {
            try {
                player.playEffect(EntityEffect.TOTEM_RESURRECT);
            } catch (Throwable ignored) {
                // the animation is cosmetic; never fail the rescue over it
            }
        }
        if (plugin.voidTotem().sound()) {
            Sound sound = plugin.soundResolver().resolve("item.totem.use");
            if (sound != null) {
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            }
        }
        if (plugin.voidTotem().particlesEnabled()) {
            Particle particle = resolveParticle(plugin.voidTotem().particleId());
            if (particle != null) {
                player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0),
                        plugin.voidTotem().particleCount(), 0.4, 0.4, 0.4, 0.01);
            }
        }
        plugin.messages().send(player, "void-totem.rescued");
        // Release the rescue lock after the cooldown so a stray void tick
        // during the rescue gap cannot consume a second totem.
        PlatformScheduler.runEntityLater(player, () -> pending.remove(id),
                plugin.voidTotem().cooldownSeconds() * 20L);
    }

    private Location computeDestination(Player player) {
        World world = player.getWorld();
        RescueMode mode = plugin.voidTotem().rescueMode();
        if (mode == RescueMode.SPAWN) {
            return world.getSpawnLocation();
        }
        if (mode == RescueMode.COORDS) {
            World target = world;
            String rescueWorld = plugin.voidTotem().rescueWorld();
            if (!rescueWorld.isEmpty()) {
                World configured = Bukkit.getWorld(rescueWorld);
                if (configured != null) {
                    target = configured;
                }
            }
            return new Location(target, plugin.voidTotem().rescueX(),
                    plugin.voidTotem().rescueY(), plugin.voidTotem().rescueZ());
        }
        Location safe = lastSafe.get(player.getUniqueId());
        return safe != null ? safe.clone() : world.getSpawnLocation();
    }

    /** Health straight back to maximum, absorption and fall distance gone. */
    private void fullHeal(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : 20.0D;
        try {
            player.setHealth(max);
        } catch (Throwable error) {
            plugin.getLogger().warning("Could not fully heal after void rescue: " + error.getMessage());
        }
        player.setAbsorptionAmount(0);
    }

    private void clearHarmful(Player player) {
        String[] harmful = {
                "poison", "wither", "slowness", "weakness", "hunger", "levitation",
                "blindness", "nausea", "mining_fatigue", "bad_luck", "darkness",
                "wind_charged", "weaving", "oozing", "infested"
        };
        for (String key : harmful) {
            PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(key));
            if (type != null) {
                player.removePotionEffect(type);
            }
        }
    }

    private void applyEffect(Player player, String key, long seconds) {
        if (seconds <= 0L) {
            return;
        }
        PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(key));
        if (type == null) {
            return;
        }
        int ticks = (int) Math.min(Integer.MAX_VALUE, seconds * 20L);
        player.addPotionEffect(new PotionEffect(type, ticks, 0));
    }

    private boolean hasVoidTotem(Player player) {
        PlayerInventory inv = player.getInventory();
        if (plugin.voidTotem().consumeFrom() == ConsumeFrom.HAND) {
            return isVoidTotem(inv.getItemInMainHand()) || isVoidTotem(inv.getItemInOffHand());
        }
        for (int i = 0; i < inv.getSize(); i++) {
            if (isVoidTotem(inv.getItem(i))) {
                return true;
            }
        }
        return isVoidTotem(inv.getItemInMainHand()) || isVoidTotem(inv.getItemInOffHand());
    }

    /** True only for a shard shop Void Totem - never a vanilla totem. */
    private boolean isVoidTotem(ItemStack stack) {
        if (stack == null || stack.getType() != Material.TOTEM_OF_UNDYING) {
            return false;
        }
        String id = plugin.items().itemId(stack);
        if (id == null) {
            return false;
        }
        ShardCatalog.Entry entry = plugin.catalog().byId(id);
        return entry != null && entry.behavior() == Behavior.VOID_TOTEM;
    }

    private boolean removeOneVoidTotem(Player player) {
        PlayerInventory inv = player.getInventory();
        if (plugin.voidTotem().consumeFrom() == ConsumeFrom.HAND) {
            ItemStack main = inv.getItemInMainHand();
            if (isVoidTotem(main)) {
                consumeHand(inv, main, true);
                return true;
            }
            ItemStack off = inv.getItemInOffHand();
            if (isVoidTotem(off)) {
                consumeHand(inv, off, false);
                return true;
            }
            return false;
        }
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isVoidTotem(stack)) {
                int amount = stack.getAmount();
                if (amount > 1) {
                    stack.setAmount(amount - 1);
                    inv.setItem(i, stack);
                } else {
                    inv.setItem(i, null);
                }
                return true;
            }
        }
        return false;
    }

    private void consumeHand(PlayerInventory inv, ItemStack stack, boolean mainHand) {
        int amount = stack.getAmount();
        if (amount > 1) {
            stack.setAmount(amount - 1);
        } else {
            stack.setType(Material.AIR);
        }
        if (mainHand) {
            inv.setItemInMainHand(stack);
        } else {
            inv.setItemInOffHand(stack);
        }
    }

    private Particle resolveParticle(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        String name = id.replace("minecraft:", "").replace('.', '_').toUpperCase(Locale.ROOT);
        try {
            // Works whether Particle is an enum or a registry-backed interface.
            return (Particle) Particle.class.getField(name).get(null);
        } catch (Throwable ignored) {
            plugin.getLogger().warning("Unknown particle '" + id + "' for the void totem rescue (skipped)");
            return null;
        }
    }
}
