package dev.superseller.voidtotem.listener;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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

import dev.superseller.voidtotem.VoidTotemPlugin;
import dev.superseller.voidtotem.config.VoidConfig;
import net.kyori.adventure.text.MiniMessage;

/**
 * Watches for players about to die in the void while carrying a Void Totem.
 * On a lethal void hit it consumes one totem and pulls the player back to a
 * safe location (last safe spot, spawn, or configured coords). All of the
 * rescue work runs on the player's own region thread via getScheduler(), so it
 * is safe on Folia as well as Paper / Purpur.
 */
@SuppressWarnings("deprecation")
public final class VoidTotemListener implements Listener {

    private final VoidTotemPlugin plugin;
    private final Map<UUID, Location> lastSafe = new HashMap<>();
    private final Map<UUID, Long> pending = new HashMap<>();

    public VoidTotemListener(VoidTotemPlugin plugin) {
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
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        if (player.isDead()) {
            return;
        }
        VoidConfig cfg = plugin.config();
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - pending.getOrDefault(id, 0L) < cfg.cooldownSeconds() * 1000L) {
            event.setCancelled(true);
            return;
        }
        // Only act when this hit would actually be lethal (health + absorption).
        double effective = player.getHealth() + player.getAbsorptionAmount();
        if (event.getFinalDamage() < effective) {
            return;
        }
        if (!hasVoidTotem(player, cfg)) {
            return;
        }
        // Consume one totem, cancel the lethal hit, and schedule the rescue.
        if (!removeOneVoidTotem(player, cfg)) {
            return;
        }
        event.setCancelled(true);
        pending.put(id, now);
        player.getScheduler().run(plugin, task -> doRescue(player, id), null);
    }

    private void doRescue(Player player, UUID id) {
        VoidConfig cfg = plugin.config();
        Location dest = computeDestination(player, cfg);
        if (dest != null) {
            player.teleport(dest);
        }
        player.setFallDistance(0);
        player.setHealth(Math.max(1.0, Math.min(player.getMaxHealth(), cfg.heal())));

        if (cfg.clearHarmful()) {
            clearHarmful(player);
        }
        applyEffect(player, "resistance", cfg.resistanceSeconds(), 0);
        applyEffect(player, "slow_falling", cfg.slowFallingSeconds(), 0);

        if (cfg.playAnimation()) {
            try {
                player.playEffect(org.bukkit.EntityEffect.TOTEM_RESURRECT);
            } catch (Throwable ignored) {
                // animation is cosmetic; ignore on exotic builds
            }
        }
        if (cfg.playSound()) {
            Sound sound = resolveSound("item.totem.use");
            if (sound != null) {
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            }
        }
        if (cfg.particles()) {
            Particle particle = resolveParticle(cfg.particleId());
            if (particle != null) {
                player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0),
                        cfg.particleCount(), 0.4, 0.4, 0.4, 0.01);
            }
        }

        String msg = cfg.message("rescued");
        if (!msg.isEmpty()) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
        }

        // Release the rescue lock after the cooldown so a stray void tick
        // during the rescue gap can't consume a second totem.
        player.getScheduler().runDelayed(plugin, task -> pending.remove(id), null, cfg.cooldownSeconds() * 20L);
    }

    private Location computeDestination(Player player, VoidConfig cfg) {
        World world = player.getWorld();
        if (cfg.rescueMode() == VoidConfig.RescueMode.SPAWN) {
            return world.getSpawnLocation();
        }
        if (cfg.rescueMode() == VoidConfig.RescueMode.COORDS) {
            World target = world;
            if (!cfg.rescueWorld().isEmpty()) {
                World w = Bukkit.getWorld(cfg.rescueWorld());
                if (w != null) {
                    target = w;
                }
            }
            return new Location(target, cfg.rescueX(), cfg.rescueY(), cfg.rescueZ());
        }
        Location safe = lastSafe.get(player.getUniqueId());
        return safe != null ? safe.clone() : world.getSpawnLocation();
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

    private void applyEffect(Player player, String key, long seconds, int amplifier) {
        if (seconds <= 0) {
            return;
        }
        PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(key));
        if (type == null) {
            return;
        }
        player.addPotionEffect(new PotionEffect(type, (int) (seconds * 20L), amplifier, true, true));
    }

    private boolean hasVoidTotem(Player player, VoidConfig cfg) {
        if (cfg.consumeFrom() == VoidConfig.ConsumeFrom.HAND) {
            return plugin.item().isVoidTotem(player.getInventory().getItemInMainHand())
                    || plugin.item().isVoidTotem(player.getInventory().getItemInOffHand());
        }
        PlayerInventory inv = player.getInventory();
        for (ItemStack stack : inv.getStorageContents()) {
            if (plugin.item().isVoidTotem(stack)) {
                return true;
            }
        }
        for (ItemStack stack : inv.getArmorContents()) {
            if (plugin.item().isVoidTotem(stack)) {
                return true;
            }
        }
        return plugin.item().isVoidTotem(inv.getItemInMainHand())
                || plugin.item().isVoidTotem(inv.getItemInOffHand());
    }

    private boolean removeOneVoidTotem(Player player, VoidConfig cfg) {
        PlayerInventory inv = player.getInventory();
        if (cfg.consumeFrom() == VoidConfig.ConsumeFrom.HAND) {
            ItemStack main = inv.getItemInMainHand();
            if (plugin.item().isVoidTotem(main)) {
                consume(inv, main, InventorySlot.MAIN_HAND);
                return true;
            }
            ItemStack off = inv.getItemInOffHand();
            if (plugin.item().isVoidTotem(off)) {
                consume(inv, off, InventorySlot.OFF_HAND);
                return true;
            }
            return false;
        }
        for (int i = 0; i < inv.getStorageContents().length; i++) {
            ItemStack stack = inv.getStorageContents()[i];
            if (plugin.item().isVoidTotem(stack)) {
                consumeSlot(inv, i, stack);
                return true;
            }
        }
        for (int i = 0; i < inv.getArmorContents().length; i++) {
            ItemStack stack = inv.getArmorContents()[i];
            if (plugin.item().isVoidTotem(stack)) {
                ItemStack[] armor = inv.getArmorContents();
                consumeStack(armor, i, stack);
                inv.setArmorContents(armor);
                return true;
            }
        }
        ItemStack main = inv.getItemInMainHand();
        if (plugin.item().isVoidTotem(main)) {
            consume(inv, main, InventorySlot.MAIN_HAND);
            return true;
        }
        ItemStack off = inv.getItemInOffHand();
        if (plugin.item().isVoidTotem(off)) {
            consume(inv, off, InventorySlot.OFF_HAND);
            return true;
        }
        return false;
    }

    private void consumeSlot(PlayerInventory inv, int index, ItemStack stack) {
        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
            inv.setItem(index, stack);
        } else {
            inv.setItem(index, null);
        }
    }

    private void consumeStack(ItemStack[] armor, int index, ItemStack stack) {
        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
            armor[index] = stack;
        } else {
            armor[index] = null;
        }
    }

    private void consume(PlayerInventory inv, ItemStack stack, InventorySlot slot) {
        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
        } else {
            stack.setType(org.bukkit.Material.AIR);
        }
        if (slot == InventorySlot.MAIN_HAND) {
            inv.setItemInMainHand(stack);
        } else {
            inv.setItemInOffHand(stack);
        }
    }

    private enum InventorySlot { MAIN_HAND, OFF_HAND }

    private static Particle resolveParticle(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        try {
            return Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(id.toLowerCase(Locale.ROOT)));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Sound resolveSound(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        try {
            return Registry.SOUND_EVENT.get(NamespacedKey.minecraft(id));
        } catch (Throwable ignored) {
            return null;
        }
    }
}
