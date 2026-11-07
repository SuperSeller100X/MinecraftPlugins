package dev.superseller.veltryx.staff;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Staff vanish: vanished players are hidden from everyone without the staff
 * permission. State survives relogs while {@code staff.vanish-persists} is on.
 */
public final class VanishService implements Listener {

    private final Plugin plugin;
    private final boolean persists;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public VanishService(Plugin plugin, boolean persists) {
        this.plugin = plugin;
        this.persists = persists;
    }

    public boolean isVanished(UUID uuid) {
        return vanished.contains(uuid);
    }

    /** Toggles vanish for a player; returns the new state. */
    public boolean toggle(Player player) {
        if (vanished.remove(player.getUniqueId())) {
            for (Player viewer : player.getServer().getOnlinePlayers()) {
                viewer.showPlayer(plugin, player);
            }
            return false;
        }
        vanished.add(player.getUniqueId());
        apply(player);
        return true;
    }

    /** Hides every vanished player from the given viewer (unless staff). */
    public void apply(Player viewer) {
        if (viewer.hasPermission("veltryx.staff.vanish")) {
            return;
        }
        for (Player online : viewer.getServer().getOnlinePlayers()) {
            if (vanished.contains(online.getUniqueId())) {
                viewer.hidePlayer(plugin, online);
            }
        }
    }

    /** Hides one vanished player from every non-staff viewer. */
    private void hideFromAll(Player target) {
        for (Player viewer : target.getServer().getOnlinePlayers()) {
            if (!viewer.hasPermission("veltryx.staff.vanish")) {
                viewer.hidePlayer(plugin, target);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (vanished.contains(player.getUniqueId()) && player.hasPermission("veltryx.staff.vanish")) {
            hideFromAll(player);
        }
        apply(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!persists) {
            vanished.remove(event.getPlayer().getUniqueId());
        }
    }
}
