package dev.superseller.hourglass.listener;

import dev.superseller.hourglass.HourGlassPlugin;
import dev.superseller.hourglass.config.Messages;
import dev.superseller.hourglass.data.PlaytimeRecord;
import dev.superseller.hourglass.scheduler.PlatformScheduler;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncChatEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

/**
 * Starts and stops measuring players, and feeds the idle detector.
 *
 * <p>Join and quit run on the player's own region thread (guaranteed by both
 * Paper and Folia), so the record work there is legal; the activity markers only
 * write a timestamp, which is why they can also be fed from async events such as
 * chat without any locking.
 */
public final class PlaytimeListener implements Listener {

    private final HourGlassPlugin plugin;

    public PlaytimeListener(HourGlassPlugin plugin) {
        this.plugin = plugin;
    }

    // ----------------------------------------------------------- session edges

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.tracking().start(player);
        plugin.playtime().persist(plugin.playtime().getOrCreate(player.getUniqueId(), player.getName()));
        if (plugin.config().debug()) {
            plugin.getLogger().info("[join] " + player.getName() + " is now tracked");
        }
        int delay = plugin.config().joinSummaryDelaySeconds();
        long delayTicks = delay * 20L;
        Runnable show = () -> sendJoinSummary(player);
        if (delayTicks > 0L) {
            PlatformScheduler.runForPlayerLater(player, delayTicks, show);
        } else {
            show.run();
        }
        plugin.milestones().check(plugin.playtime().of(player.getUniqueId()));
    }

    private void sendJoinSummary(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!plugin.config().joinSummary() || !player.hasPermission("hourglass.notify.self")) {
            return;
        }
        PlaytimeRecord record = plugin.playtime().of(player.getUniqueId());
        if (record == null) {
            return;
        }
        plugin.messages().send(player, "join.summary", plugin.gui().placeholdersFor(record));
        if (plugin.config().joinSummarySound()) {
            plugin.sounds().play(player, "join");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlaytimeRecord before = plugin.playtime().of(player.getUniqueId());
        plugin.tracking().stop(player);
        plugin.display().clear(player.getUniqueId());
        if (before == null || !plugin.config().quitStaffBroadcast()) {
            return;
        }
        long now = System.currentTimeMillis();
        plugin.messages().broadcastPermission("hourglass.notify.staff", "quit.staff-summary",
                Messages.ph("player", before.name() == null ? "?" : before.name(),
                        "session", plugin.config().timeFormat().format(before.sessionTotalSeconds(now)),
                        "session-active", plugin.config().timeFormat().format(before.sessionActiveSeconds()),
                        "total", plugin.config().timeFormat().format(before.totalSeconds()),
                        "active", plugin.config().timeFormat().format(before.activeSeconds())));
    }

    // ------------------------------------------------------------ activity pings

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock() && !event.hasChangedOrientation()) {
            return; // looking around while standing still is not activity
        }
        mark(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        mark(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        mark(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        mark(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        mark(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        mark(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            mark(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        mark(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        mark(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        mark(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        mark(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            mark(player);
        }
    }

    /** Records "this player is doing something" for the idle detector. */
    private void mark(Player player) {
        if (player == null) {
            return;
        }
        try {
            plugin.tracking().markActivity(player.getUniqueId());
        } catch (RuntimeException e) {
            if (plugin.config().debug()) {
                plugin.getLogger().warning("[activity] " + e.getMessage());
            }
        }
    }
}
