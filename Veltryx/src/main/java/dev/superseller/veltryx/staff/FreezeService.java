package dev.superseller.veltryx.staff;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Freeze: locks a player in place (head rotation still allowed) and cancels
 * every interaction, damage and teleport while frozen.
 */
public final class FreezeService implements Listener {

    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

    public boolean isFrozen(UUID uuid) {
        return frozen.contains(uuid);
    }

    /** Toggles the frozen state; returns the new state. */
    public boolean toggle(Player player) {
        if (frozen.contains(player.getUniqueId())) {
            frozen.remove(player.getUniqueId());
            return false;
        }
        frozen.add(player.getUniqueId());
        return true;
    }

    /** Freezes or unfreezes explicitly. */
    public void setFrozen(Player player, boolean value) {
        if (value) {
            frozen.add(player.getUniqueId());
        } else {
            frozen.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!frozen.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) {
            event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(),
                    to.getYaw(), to.getPitch()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (frozen.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (frozen.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (frozen.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (frozen.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && frozen.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
