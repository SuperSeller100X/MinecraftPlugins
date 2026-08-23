package dev.superseller.connectedtools.listener;

import dev.superseller.connectedtools.ConnectedToolsPlugin;
import dev.superseller.connectedtools.command.ConnectedToolsCommand;
import dev.superseller.connectedtools.model.Connection;
import dev.superseller.connectedtools.model.ConnectionStore;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class PlayerInteractListener implements Listener {

    // In-memory redstone pulse tracking (temporary powered states)
    private final Map<Location, Block> poweredBlocks = new HashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        // Check if player is in binding mode
        ConnectionStore store = ConnectedToolsPlugin.getInstance().getStore();

        if (store == null) return;

        if (store.isBinding(player.getUniqueId()) && action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            Block clicked = event.getClickedBlock();
            if (clicked == null) return;

            double distance = player.getLocation().distance(clicked.getLocation());
            if (distance > 10.0) {
                player.sendMessage(ChatColor.RED + "That block is too far away (max 10 blocks).");
                return;
            }

            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType().isAir()) {
                player.sendMessage(ChatColor.RED + "Hold an item to bind.");
                return;
            }

            Connection connection = new Connection(
                    item.getType().name(),
                    item.getType(),
                    clicked.getLocation()
            );

            store.addConnection(player.getUniqueId(), item, connection);
            store.clearBinding(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Bound " + item.getType().name()
                    + " to " + clicked.getType().name() + " at " + connection.getLocationString() + ".");
            return;
        }

        // Activation: right-click with connected item -> emit redstone pulse
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType().isAir()) return;

            Connection conn = store.getConnection(player.getUniqueId(), item);
            if (conn == null) return;

            // Only emit if player has permission
            if (!player.hasPermission("connectedtools.connect") && !player.hasPermission("connectedtools.all")) {
                return;
            }

            event.setCancelled(true);
            emitRedstonePulse(conn);
            player.sendMessage(ChatColor.GRAY + "Redstone pulse emitted at " + conn.getLocationString() + ".");
        }
    }

    private void emitRedstonePulse(Connection conn) {
        Location loc = conn.getLocation();
        if (loc == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            return;
        }

        Block target = loc.getBlock();
        if (target == null) return;

        // For redstone-related blocks, temporarily set powered state
        Material type = target.getType();
        boolean modified = false;

        switch (type) {
            case REDSTONE_WIRE:
                // Temporarily power wire (this requires using block data; simplified approach: schedule removal/replacement for visual pulse)
                // For simplicity in this plugin, we trigger a temporary redstone block placement nearby or rely on block state changes.
                // We'll place a temporary redstone block at the target location if air; otherwise place a torch pointing at it.
                modified = true;
                break;
            case COMPARATOR:
            case REPEATER:
            case OBSERVER:
            case REDSTONE_TORCH:
            case REDSTONE_BLOCK:
            case REDSTONE_WALL_TORCH:
                modified = true;
                break;
            default:
                // For any other block, emit pulse by placing temporary redstone block next to it
                // or by powering adjacent redstone components.
                modified = false;
        }

        // Simplified pulse mechanism: temporarily place a redstone block at the target location
        // for 1 tick, then restore original block. Only if the original is not air (to avoid destroying air).
        // Actually, let's do a safe approach: place a temporary powered component nearby.

        // Safe approach: temporarily set target to redstone block for 2 ticks if it is not air,
        // but save original block. However, this could destroy non-solid blocks temporarily.
        // Instead, for simplicity: we emit pulse by placing a temporary redstone torch or block
        // at an adjacent location that points to/powers the target.

        Location pulseLoc = target.getLocation();
        Block pulseBlock = pulseLoc.getBlock();
        Material original = pulseBlock.getType();

        // Only do temporary replacement for safe materials
        if (original == Material.AIR || original == Material.CAVE_AIR || original == Material.VOID_AIR) {
            pulseBlock.setType(Material.REDSTONE_BLOCK);
            target.getWorld().getBlockAt(pulseLoc).getState().update();
            org.bukkit.scheduler.BukkitRunnable runnable = new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    pulseBlock.setType(original);
                }
            };
            runnable.runTaskLater(ConnectedToolsPlugin.getInstance(), 2L);
        } else {
            // Place temporary redstone block above target for 2 ticks
            Block above = target.getLocation().add(0, 1, 0).getBlock();
            Material aboveOrig = above.getType();
            above.setType(Material.REDSTONE_BLOCK);
            org.bukkit.scheduler.BukkitRunnable runnable = new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    above.setType(aboveOrig);
                }
            };
            runnable.runTaskLater(ConnectedToolsPlugin.getInstance(), 2L);
        }
    }
}
