package dev.superseller.swifttpa.listener;

import dev.superseller.swifttpa.SwiftTPAPlugin;
import dev.superseller.swifttpa.request.WarmupManager;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Cancels an active teleport warmup when the counting-down player moves to
 * another block or takes damage (both individually configurable). Checked at
 * MONITOR so the decision reflects the final, uncancelled event state.
 */
public final class WarmupListener implements Listener {

    private final SwiftTPAPlugin plugin;

    public WarmupListener(SwiftTPAPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.tpaConfig().cancelOnMove() || !event.hasChangedBlock()) {
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        if (plugin.service().warmups().isWarmingUp(uuid)) {
            plugin.service().warmups().cancel(uuid, WarmupManager.CancelReason.MOVED);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!plugin.tpaConfig().cancelOnDamage()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (plugin.service().warmups().isWarmingUp(uuid)) {
            plugin.service().warmups().cancel(uuid, WarmupManager.CancelReason.DAMAGE);
        }
    }
}
