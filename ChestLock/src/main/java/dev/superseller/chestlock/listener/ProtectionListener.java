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
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Defense-in-depth protection for player, environmental, and optional automation access paths. */
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
    public void onPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (!lockStore.isContainer(placed.getType())) return;

        // Tile-state data may be copied into a shulker item (or a creative clone). A placed copy must
        // never duplicate the original lock UUID. Clear only the new block before checking neighbours.
        lockStore.clearCopiedMetadata(placed);
        LockStore.Lookup lookup = lockStore.lookup(placed);
        if (!lookup.secured()) return;

        // A new chest was placed beside a locked single chest and would merge into its protected inventory.
        event.setCancelled(true);
        feedback.locked(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDrops(BlockDropItemEvent event) {
        // Breaking a locked shulker is allowed after passcode authorization, but the dropped item must
        // not carry a cloneable live lock. Container contents and all unrelated item metadata remain intact.
        event.getItems().forEach(item -> {
            ItemStack stack = item.getItemStack();
            if (lockStore.clearCopiedMetadata(stack)) {
                item.setItemStack(stack);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        if (plugin.runtimeConfig().protectExplosions()) {
            event.blockList().removeIf(this::isSecured);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        if (plugin.runtimeConfig().protectExplosions()) {
            event.blockList().removeIf(this::isSecured);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (plugin.runtimeConfig().protectPistons() && event.getBlocks().stream().anyMatch(this::isSecured)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (plugin.runtimeConfig().protectPistons() && event.getBlocks().stream().anyMatch(this::isSecured)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (plugin.runtimeConfig().protectFire() && isSecured(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (plugin.runtimeConfig().protectEntityChanges() && isSecured(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (!plugin.runtimeConfig().blockHoppers()) return;
        Block source = lockStore.blockForInventory(event.getSource());
        Block destination = lockStore.blockForInventory(event.getDestination());
        if ((source != null && isSecured(source)) || (destination != null && isSecured(destination))) {
            event.setCancelled(true);
        }
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
