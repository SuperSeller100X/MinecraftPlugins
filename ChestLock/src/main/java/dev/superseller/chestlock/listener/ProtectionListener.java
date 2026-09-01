package dev.superseller.chestlock.listener;

import dev.superseller.chestlock.ChestLockPlugin;
import dev.superseller.chestlock.gui.DialogController;
import dev.superseller.chestlock.message.Feedback;
import dev.superseller.chestlock.model.LockData;
import dev.superseller.chestlock.security.AttemptLimiter;
import dev.superseller.chestlock.security.SessionManager;
import dev.superseller.chestlock.service.KeyService;
import dev.superseller.chestlock.storage.LockStore;
import dev.superseller.chestlock.storage.PlayerSettingsStore;
import java.util.Map;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Unconditional, absolute defense-in-depth protection making locked containers literally impossible to destroy or open when not unlocked. */
public final class ProtectionListener implements Listener {
    private final ChestLockPlugin plugin;
    private final LockStore lockStore;
    private final PlayerSettingsStore settings;
    private final Feedback feedback;
    private final KeyService keys;
    private final SessionManager sessions;
    private final AttemptLimiter attempts;
    private final DialogController dialogs;

    public ProtectionListener(
            ChestLockPlugin plugin,
            LockStore lockStore,
            PlayerSettingsStore settings,
            Feedback feedback,
            KeyService keys,
            SessionManager sessions,
            AttemptLimiter attempts,
            DialogController dialogs
    ) {
        this.plugin = plugin;
        this.lockStore = lockStore;
        this.settings = settings;
        this.feedback = feedback;
        this.keys = keys;
        this.sessions = sessions;
        this.attempts = attempts;
        this.dialogs = dialogs;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() || event.getClickedBlock() == null) {
            return;
        }
        LockStore.Lookup lookup = lockStore.lookup(event.getClickedBlock());
        if (!lookup.secured()) return;
        Player player = event.getPlayer();
        if (sessions.hasBypass(player.getUniqueId())) return;
        LockData lock = lookup.data();
        if (lock != null && (sessions.isAuthorized(player.getUniqueId(), lock.lockId(), System.currentTimeMillis())
                || keys.usedValidKey(player, event.getHand(), lock))) {
            return;
        }
        event.setCancelled(true);
        feedback.locked(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Block block = lockStore.blockForInventory(event.getInventory());
        if (block == null) return;
        LockStore.Lookup lookup = lockStore.lookup(block);
        if (!lookup.secured() || sessions.hasBypass(player.getUniqueId())) return;
        LockData lock = lookup.data();
        if (lock != null && (sessions.isAuthorized(player.getUniqueId(), lock.lockId(), System.currentTimeMillis())
                || keys.playerHoldsValidKey(player, lock))) {
            return;
        }
        event.setCancelled(true);
        feedback.locked(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        LockStore.Lookup lookup = lockStore.lookup(event.getBlock());
        if (!lookup.secured()) return;
        Player player = event.getPlayer();
        if (sessions.hasBypass(player.getUniqueId())) return;
        LockData lock = lookup.data();
        if (lock == null || !sessions.isAuthorized(player.getUniqueId(), lock.lockId(), System.currentTimeMillis())) {
            event.setCancelled(true);
            feedback.locked(player);
            return;
        }
        if (settings.get(player.getUniqueId()).breakConfirmation()
                && !sessions.confirmBreak(player.getUniqueId(), lock.lockId(), System.currentTimeMillis(),
                plugin.runtimeConfig().breakConfirmationMillis())) {
            event.setCancelled(true);
            feedback.message(player, "break-confirm", Map.of("seconds", Long.toString(
                    Math.max(1, plugin.runtimeConfig().breakConfirmationMillis() / 1_000))));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        LockStore.Lookup lookup = lockStore.lookup(event.getBlock());
        if (!lookup.secured()) return;
        Player player = event.getPlayer();
        if (sessions.hasBypass(player.getUniqueId())) return;
        LockData lock = lookup.data();
        if (lock != null && sessions.isAuthorized(player.getUniqueId(), lock.lockId(), System.currentTimeMillis())) {
            return;
        }
        event.setCancelled(true);
        if (lock != null) {
            feedback.locked(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (!lockStore.isContainer(placed.getType())) return;

        lockStore.clearCopiedMetadata(placed);
        LockStore.Lookup lookup = lockStore.lookup(placed);
        if (!lookup.secured()) return;

        event.setCancelled(true);
        feedback.locked(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDrops(BlockDropItemEvent event) {
        event.getItems().forEach(item -> {
            ItemStack stack = item.getItemStack();
            if (lockStore.clearCopiedMetadata(stack)) {
                item.setItemStack(stack);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isSecured);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isSecured);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (isSecured(block)) {
                event.setCancelled(true);
                return;
            }
            Block target = block.getRelative(event.getDirection());
            if (isSecured(target)) {
                event.setCancelled(true);
                return;
            }
        }
        Block front = event.getBlock().getRelative(event.getDirection());
        if (isSecured(front)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (isSecured(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (isSecured(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.getBlock() != null && isSecured(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isSecured(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Block source = lockStore.blockForInventory(event.getSource());
        Block destination = lockStore.blockForInventory(event.getDestination());
        if ((source != null && isSecured(source)) || (destination != null && isSecured(destination))) {
            event.setCancelled(true);
            return;
        }
        if (isInventoryNearSecuredContainer(event.getSource()) || isInventoryNearSecuredContainer(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryPickupItem(InventoryPickupItemEvent event) {
        Block block = lockStore.blockForInventory(event.getInventory());
        if ((block != null && isSecured(block)) || isInventoryNearSecuredContainer(event.getInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent event) {
        if (event.getBlock().getBlockData() instanceof org.bukkit.block.data.type.Dispenser dispenser) {
            Block target = event.getBlock().getRelative(dispenser.getFacing());
            if (isSecured(target)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (isSecured(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (isSecured(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        Player player = event.getPlayer();

        if (lockStore.isContainer(placed.getType())) {
            lockStore.clearCopiedMetadata(placed);
            LockStore.Lookup lookup = lockStore.lookup(placed);
            if (lookup.secured()) {
                event.setCancelled(true);
                feedback.locked(player);
                return;
            }
        }

        if (isContainerDevice(placed.getType())) {
            if (!sessions.hasBypass(player.getUniqueId())) {
                for (Block adjacent : getAdjacentOrTargetContainers(placed)) {
                    LockStore.Lookup lookup = lockStore.lookup(adjacent);
                    if (lookup.secured()) {
                        LockData lock = lookup.data();
                        if (lock == null || !sessions.isAuthorized(player.getUniqueId(), lock.lockId(), System.currentTimeMillis())) {
                            event.setCancelled(true);
                            feedback.locked(player);
                            return;
                        }
                    }
                }
            }
        }
    }

    private boolean isContainerDevice(org.bukkit.Material material) {
        return material == org.bukkit.Material.HOPPER
                || material == org.bukkit.Material.DROPPER
                || material == org.bukkit.Material.DISPENSER;
    }

    private java.util.List<Block> getAdjacentOrTargetContainers(Block block) {
        java.util.List<Block> containers = new java.util.ArrayList<>();
        org.bukkit.block.BlockFace[] faces = {
            org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN,
            org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
            org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST
        };
        for (org.bukkit.block.BlockFace face : faces) {
            Block rel = block.getRelative(face);
            if (lockStore.isContainer(rel.getType())) {
                containers.add(rel);
            }
        }
        if (block.getBlockData() instanceof org.bukkit.block.data.Directional directional) {
            Block target = block.getRelative(directional.getFacing());
            if (lockStore.isContainer(target.getType()) && !containers.contains(target)) {
                containers.add(target);
            }
        }
        return containers;
    }

    private boolean isInventoryNearSecuredContainer(Inventory inventory) {
        if (inventory == null) return false;
        Block block = lockStore.blockForInventory(inventory);
        if (block != null) {
            return isSecured(block);
        }
        org.bukkit.inventory.InventoryHolder holder = inventory.getHolder();
        if (holder instanceof org.bukkit.entity.Entity entity) {
            Block entBlock = entity.getLocation().getBlock();
            if (isSecured(entBlock)) return true;
            for (Block adj : getAdjacentOrTargetContainers(entBlock)) {
                if (isSecured(adj)) return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (keys.isChestLockKey(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.runtimeConfig().clearSessionsOnQuit()) {
            sessions.clearPlayer(event.getPlayer().getUniqueId());
        } else {
            sessions.disableBypass(event.getPlayer().getUniqueId());
        }
        attempts.clearPlayer(event.getPlayer().getUniqueId());
        dialogs.clearPlayer(event.getPlayer().getUniqueId());
        feedback.clearPlayer(event.getPlayer().getUniqueId());
    }

    private boolean isSecured(Block block) {
        return lockStore.isContainer(block.getType()) && lockStore.lookup(block).secured();
    }
}
