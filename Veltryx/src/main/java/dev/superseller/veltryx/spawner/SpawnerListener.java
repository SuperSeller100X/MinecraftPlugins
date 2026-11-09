package dev.superseller.veltryx.spawner;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.util.PlatformScheduler;

/** Connects permanent spawner items, block events, native spawning and loot storage. */
public final class SpawnerListener implements Listener {
    private record Pending(SpawnerItems.Identity identity, SpawnerStore.Position position, ItemStack pickup) { }

    private final SpawnerItems items;
    private final SpawnerStore store;
    private final SpawnerLootGui gui;
    private final Messages messages;
    private final Logger logger;
    private final Map<BlockPlaceEvent, Pending> placements = new ConcurrentHashMap<>();
    private final Map<BlockBreakEvent, Pending> breaks = new ConcurrentHashMap<>();
    private final Set<java.util.UUID> repairs = ConcurrentHashMap.newKeySet();

    public SpawnerListener(SpawnerItems items, SpawnerStore store, SpawnerLootGui gui,
                           Messages messages, Logger logger) {
        this.items = items; this.store = store; this.gui = gui; this.messages = messages; this.logger = logger;
        gui.attachModeHandler(this::requestToggle);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        SpawnerItems.Identity identity = items.identity(event.getItemInHand());
        if (identity == null) return;
        if (!event.canBuild()) { event.setCancelled(true); return; }
        Block block = event.getBlockPlaced();
        SpawnerStore.Position position = SpawnerStore.Position.of(block);
        if (!store.bind(identity, position)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "spawner.unavailable", Map.of());
            return;
        }
        // Paper can install captured block entities after BlockPlaceEvent.
        // Configure only after placement commits, on the block's owning region.
        placements.put(event, new Pending(identity, position, null));
    }

    /** If a later protection listener cancels placement, return the identity to its item. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlaceFinished(BlockPlaceEvent event) {
        Pending pending = placements.remove(event);
        if (pending == null) return;
        if (event.isCancelled() || !event.canBuild()) {
            if (!store.unbind(pending.identity(), pending.position())) {
                logger.severe("Could not roll back a cancelled spawner placement: " + pending.identity().id());
            }
            return;
        }
        Block block = event.getBlockPlaced();
        try {
            PlatformScheduler.runForLocation(block.getLocation(), () -> finishPlacement(event, pending, block));
        } catch (RuntimeException ex) {
            // If scheduling is unavailable, cancel before the placement commits.
            store.unbind(pending.identity(), pending.position());
            event.setCancelled(true);
            logger.log(Level.SEVERE, "Could not schedule custom spawner placement", ex);
            messages.send(event.getPlayer(), "spawner.storage-error", Map.of());
        }
    }

    private void finishPlacement(BlockPlaceEvent event, Pending pending, Block block) {
        SpawnerItems.Identity identity = pending.identity();
        SpawnerStore.Position position = pending.position();
        // Respect even a plugin that improperly cancels after our MONITOR listener.
        if (event.isCancelled() || !event.canBuild()) {
            store.unbind(identity, position);
            return;
        }
        if (store.modeAt(identity, position) == null) return;
        try {
            SpawnerItems.Identity current = items.identity(block);
            int count = store.stackSizeAt(identity, position);
            SpawnerStore.Mode mode = store.modeAt(identity, position);
            if (block.getType() == Material.SPAWNER && (current == null || current.equals(identity))
                    && count > 0 && mode != null && items.configure(block, identity, count, mode)) return;
        } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, "Could not configure placed spawner " + identity.id(), ex);
        }
        // Another plugin/player may have replaced the block before this task.
        // Return the same identity, so its existing stored loot stays attached.
        if (!store.unbind(identity, position)) {
            logger.severe("Could not recover placed spawner " + identity.id() + "; restore from backup");
            return;
        }
        SpawnerItems.Identity current = items.identity(block);
        if (block.getType() == Material.SPAWNER && (current == null || current.equals(identity))) {
            block.setType(Material.AIR);
        }
        org.bukkit.entity.Item refund = block.getWorld().dropItemNaturally(block.getLocation(), items.create(identity, store.stackSize(identity)));
        Player player = event.getPlayer();
        refund.setOwner(player.getUniqueId());
        refund.setUnlimitedLifetime(true);
        refund.setInvulnerable(true);
        PlatformScheduler.runForPlayer(player,
                () -> messages.send(player, "spawner.placement-refund", Map.of()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        SpawnerItems.Identity identity = items.identity(event.getBlock());
        if (identity == null) return;
        Player player = event.getPlayer();
        SpawnerStore.Position position = SpawnerStore.Position.of(event.getBlock());
        if (store.modeAt(identity, position) == null) {
            event.setCancelled(true);
            messages.send(player, "spawner.unavailable", Map.of());
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) <= 0) {
            event.setCancelled(true);
            messages.send(player, "spawner.need-silk", Map.of());
            return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            event.setCancelled(true);
            messages.send(player, "spawner.inventory-full", Map.of());
            return;
        }
        try {
            ItemStack pickup = items.create(identity, store.stackSizeAt(identity, position));
            event.setDropItems(false);
            event.setExpToDrop(0);
            breaks.put(event, new Pending(identity, position, pickup));
        } catch (RuntimeException e) {
            event.setCancelled(true);
            logger.log(Level.SEVERE, "Could not prepare spawner pickup " + identity.id(), e);
            messages.send(player, "spawner.storage-error", Map.of());
        }
    }

    /** Deliver only after other protection listeners have had a chance to cancel. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreakFinished(BlockBreakEvent event) {
        Pending pending = breaks.remove(event);
        if (pending == null || event.isCancelled()) return;
        // Do not let a later non-cancelling listener restore vanilla drops.
        event.setDropItems(false);
        event.setExpToDrop(0);
        Player player = event.getPlayer();
        if (!store.unbind(pending.identity(), pending.position())) {
            // Exceptional disk failure: leave the block in place rather than lose it.
            event.setCancelled(true);
            messages.send(player, "spawner.storage-error", Map.of());
            return;
        }
        try {
            if (!player.getInventory().addItem(pending.pickup()).isEmpty()) {
                if (!store.bind(pending.identity(), pending.position())) {
                    logger.severe("Could not restore spawner after failed pickup: " + pending.identity().id());
                }
                event.setCancelled(true);
                messages.send(player, "spawner.inventory-full", Map.of());
                return;
            }
        } catch (RuntimeException e) {
            store.bind(pending.identity(), pending.position());
            event.setCancelled(true);
            logger.log(Level.SEVERE, "Could not deliver spawner pickup " + pending.identity().id(), e);
            messages.send(player, "spawner.storage-error", Map.of());
            return;
        }
        messages.send(player, "spawner.picked-up", Map.of("type", pending.identity().type().displayName()));
        gui.refreshViewers(pending.identity().id());
    }

    // Finalize at MONITOR so protection plugins running through HIGHEST can
    // deny access before we cancel vanilla use and schedule the storage GUI.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.isCancelled() || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        SpawnerItems.Identity identity = items.identity(block);
        if (identity == null) return;
        // Also cancel off-hand interactions so spawn eggs cannot change the type.
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        SpawnerStore.Position position = SpawnerStore.Position.of(block);
        if (store.modeAt(identity, position) == null) {
            messages.send(event.getPlayer(), "spawner.unavailable", Map.of());
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        SpawnerItems.Identity source = items.identity(held);
        if (source != null) {
            if (held.getAmount() != 1) { messages.send(player, "spawner.unavailable", Map.of()); return; }
            SpawnerStore.Merge result = store.merge(identity, position, source);
            switch (result) {
                case DIFFERENT_TYPE -> messages.send(player, "spawner.stack-wrong-type", Map.of());
                case FULL -> messages.send(player, "spawner.stack-full", Map.of("max", Integer.toString(SpawnerStore.MAX_STACK)));
                case ERROR -> messages.send(player, "spawner.storage-error", Map.of());
                case INVALID -> messages.send(player, "spawner.unavailable", Map.of());
                case OK -> {
                    // The parent link is already durable: copied/old source items can no longer be placed.
                    player.getInventory().setItemInMainHand(null);
                    int count = store.stackSizeAt(identity, position);
                    SpawnerStore.Mode mode = store.modeAt(identity, position);
                    if (!items.updateCounts(block, identity, count, mode)) requestRepair(identity, position, block.getLocation());
                    messages.send(player, "spawner.stacked", Map.of("count", Integer.toString(count),
                            "max", Integer.toString(SpawnerStore.MAX_STACK)));
                    gui.refreshViewers(identity.id());
                }
            }
            return;
        }
        gui.open(player, identity, position, block.getLocation(), 0);
    }

    /** Change mode and native spawn parameters on the spawner's owning region. */
    private void requestToggle(Player player, SpawnerItems.Identity identity, SpawnerStore.Position position, Location location) {
        try {
            PlatformScheduler.runForLocation(location, () -> {
                Block block = location.getBlock();
                if (!identity.equals(items.identity(block))) {
                    PlatformScheduler.runForPlayer(player, () -> messages.send(player, "spawner.unavailable", Map.of()));
                    return;
                }
                SpawnerStore.Mode before = store.modeAt(identity, position);
                int count = store.stackSizeAt(identity, position);
                SpawnerStore.Mode next = before == null || count == 0 ? null : store.toggle(identity, position);
                if (next == null) {
                    PlatformScheduler.runForPlayer(player, () -> messages.send(player, "spawner.unavailable", Map.of()));
                    return;
                }
                if (!items.updateCounts(block, identity, count, next)) {
                    if (store.toggle(identity, position) != before) logger.severe("Could not roll back spawner mode for " + identity.id());
                    requestRepair(identity, position, location);
                    PlatformScheduler.runForPlayer(player, () -> messages.send(player, "spawner.storage-error", Map.of()));
                } else {
                    PlatformScheduler.runForPlayer(player, () -> messages.send(player, "spawner.mode",
                            Map.of("mode", next == SpawnerStore.Mode.SPAWNER ? "Spawner" : "Inventory")));
                }
                gui.refreshViewers(identity.id());
            });
        } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, "Could not schedule spawner mode change", ex);
            messages.send(player, "spawner.storage-error", Map.of());
        }
    }

    /** Never allow a stale native spawn count to multiply output twice. */
    private void requestRepair(SpawnerItems.Identity identity, SpawnerStore.Position position, Location location) {
        if (!repairs.add(identity.id())) return;
        try {
            PlatformScheduler.runForLocation(location, () -> {
                try {
                    Block block = location.getBlock();
                    int count = store.stackSizeAt(identity, position);
                    SpawnerStore.Mode mode = store.modeAt(identity, position);
                    if (count > 0 && mode != null && identity.equals(items.identity(block))
                            && !items.updateCounts(block, identity, count, mode)) {
                        logger.severe("Could not repair spawner spawn parameters " + identity.id());
                    }
                } finally { repairs.remove(identity.id()); }
            });
        } catch (RuntimeException ex) {
            repairs.remove(identity.id());
            logger.log(Level.SEVERE, "Could not schedule spawner repair " + identity.id(), ex);
        }
    }

    // Convert only after normal-priority and HIGHEST spawn protections have
    // had an opportunity to cancel this event. MONITOR is used here to make
    // the conversion itself the last standard event decision.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(SpawnerSpawnEvent event) {
        if (event.isCancelled()) return;
        CreatureSpawner spawner = event.getSpawner();
        SpawnerItems.Identity identity = items.identity(spawner);
        if (identity == null) return; // Vanilla or another plugin's spawner.
        SpawnerStore.Position position = SpawnerStore.Position.of(spawner.getBlock());
        SpawnerStore.Mode mode = store.modeAt(identity, position);
        int count = store.stackSizeAt(identity, position);
        if (mode == null || count == 0 || event.getEntity().getType() != identity.type().entityType()) {
            event.setCancelled(true); return;
        }
        if (spawner.getSpawnCount() != SpawnerItems.spawnCount(count, mode)
                || spawner.getMaxNearbyEntities() != SpawnerItems.nearbyLimit(count, mode)) {
            event.setCancelled(true);
            requestRepair(identity, position, spawner.getBlock().getLocation());
            return;
        }
        if (mode == SpawnerStore.Mode.SPAWNER) return;

        // Paper creates the real mob before this event, so its own loot context
        // (including sheep colour) is available without actually spawning it.
        event.setCancelled(true);
        try {
            LootTable table = Bukkit.getLootTable(NamespacedKey.minecraft(identity.type().lootTable()));
            if (table == null) {
                logger.severe("Missing loot table for " + identity.type() + " custom spawner");
                return;
            }
            LootContext context = new LootContext.Builder(event.getEntity().getLocation())
                    .lootedEntity(event.getEntity()).build();
            List<ItemStack> loot = new ArrayList<>();
            // Inventory mode keeps the native count at four; independently roll
            // once per represented spawner for every native attempt.
            for (int i = 0; i < count; i++) {
                Collection<ItemStack> roll = table.populateLoot(ThreadLocalRandom.current(), context);
                for (ItemStack item : roll) combine(loot, item);
            }
            SpawnerStore.Deposit result = store.deposit(identity, position, loot);
            if (result == SpawnerStore.Deposit.CHANGED_MODE) {
                // A remote viewer switched modes while the loot roll was in progress.
                event.setCancelled(false);
            } else if (result == SpawnerStore.Deposit.STORED) {
                gui.refreshViewers(identity.id());
            }
            // FULL/ERROR/INVALID: cancel this attempt without losing part of a roll.
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not generate loot for custom spawner " + identity.id(), e);
        }
    }

    private static void combine(List<ItemStack> batch, ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) return;
        int remaining = item.getAmount();
        for (ItemStack stored : batch) {
            if (!stored.isSimilar(item)) continue;
            int add = Math.min(remaining, Integer.MAX_VALUE - stored.getAmount());
            stored.setAmount(stored.getAmount() + add); remaining -= add;
            if (remaining == 0) return;
        }
        ItemStack copy = item.clone(); copy.setAmount(remaining); batch.add(copy);
    }

    /** Explosions cannot silently destroy a purchased spawner or its contents. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> items.identity(block) != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> items.identity(block) != null);
    }
}
