package dev.superseller.veltryx.staff;

import dev.superseller.veltryx.util.PlatformScheduler;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
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
 *
 * <p>Enforcement is deliberately three-layered so freezing works on any server
 * fork regardless of movement-event semantics: (1) movement and teleport
 * events are cancelled, (2) the position is snapped back, and (3) a periodic
 * guard teleports anyone who drifted back to their freeze anchor.
 */
public final class FreezeService implements Listener {

    /** Frozen players mapped to the location they were frozen at. */
    private final Map<UUID, Location> frozen = new ConcurrentHashMap<>();

    public boolean isFrozen(UUID uuid) {
        return frozen.containsKey(uuid);
    }

    /** Toggles the frozen state; returns the new state. */
    public boolean toggle(Player player) {
        if (frozen.remove(player.getUniqueId()) != null) {
            return false;
        }
        freeze(player);
        return true;
    }

    /** Freezes or unfreezes explicitly. */
    public void setFrozen(Player player, boolean value) {
        if (value) {
            freeze(player);
        } else {
            frozen.remove(player.getUniqueId());
        }
    }

    private void freeze(Player player) {
        frozen.put(player.getUniqueId(), player.getLocation().clone());
    }

    /**
     * Starts the periodic guard (every 10 ticks): any frozen player who is not
     * at their freeze anchor is teleported back. Runs regardless of movement
     * events, so freezing cannot be bypassed by client movement tricks.
     */
    public void startGuard() {
        PlatformScheduler.runTimer(() -> {
            for (Map.Entry<UUID, Location> entry : frozen.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null) {
                    continue;
                }
                Location anchor = entry.getValue();
                Location now = player.getLocation();
                if (now.getWorld() != anchor.getWorld()
                        || now.getX() != anchor.getX()
                        || now.getY() != anchor.getY()
                        || now.getZ() != anchor.getZ()) {
                    PlatformScheduler.runForPlayer(player, () -> {
                        if (frozen.containsKey(player.getUniqueId())) {
                            player.teleportAsync(anchor);
                        }
                    });
                }
            }
        }, 10L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location anchor = frozen.get(event.getPlayer().getUniqueId());
        if (anchor == null) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setCancelled(true);
            event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(),
                    to.getYaw(), to.getPitch()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location anchor = frozen.get(event.getPlayer().getUniqueId());
        if (anchor == null) {
            return;
        }
        Location to = event.getTo();
        // Allow the guard's own teleport back to the freeze anchor.
        if (to != null && to.getWorld() == anchor.getWorld()
                && to.getX() == anchor.getX() && to.getY() == anchor.getY() && to.getZ() == anchor.getZ()) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (frozen.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (frozen.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (frozen.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && frozen.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
