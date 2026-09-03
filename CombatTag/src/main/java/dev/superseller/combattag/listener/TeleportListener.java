package dev.superseller.combattag.listener;

import dev.superseller.combattag.combat.BlockedAction;
import dev.superseller.combattag.combat.CombatManager;
import dev.superseller.combattag.combat.RestrictionService;
import dev.superseller.combattag.config.PluginConfig;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Cancels teleports of every origin (plugin teleports from /home, /warp, /tpa, /spawn, /back,
 * ender pearls, chorus fruit ...) while a player is combat tagged.
 */
public final class TeleportListener implements Listener {

    private final PluginConfig config;
    private final CombatManager combat;
    private final RestrictionService restrictions;

    public TeleportListener(PluginConfig config, CombatManager combat, RestrictionService restrictions) {
        this.config = config;
        this.combat = combat;
        this.restrictions = restrictions;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!config.isBlockTeleports()) {
            return;
        }
        Player player = event.getPlayer();
        if (!combat.isTagged(player.getUniqueId())) {
            return;
        }
        String cause = event.getCause() != null ? event.getCause().name() : "UNKNOWN";
        if (restrictions.isTeleportCauseAllowed(cause)) {
            return;
        }
        if (restrictions.denyIfBlocked(player, BlockedAction.TELEPORT, cause)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearlLaunch(ProjectileLaunchEvent event) {
        if (!config.isBlockTeleports() || !config.isBlockEnderPearls()) {
            return;
        }
        if (!(event.getEntity() instanceof EnderPearl pearl)) {
            return;
        }
        if (!(pearl.getShooter() instanceof Player player)) {
            return;
        }
        if (restrictions.denyIfBlocked(player, BlockedAction.PEARL, "ENDER_PEARL")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChorusFruit(PlayerInteractEvent event) {
        if (!config.isBlockTeleports() || !config.isBlockChorusFruit()) {
            return;
        }
        if (event.getItem() == null || event.getItem().getType() != Material.CHORUS_FRUIT) {
            return;
        }
        if (!event.getAction().isRightClick()) {
            return;
        }
        if (restrictions.denyIfBlocked(event.getPlayer(), BlockedAction.PEARL, "CHORUS_FRUIT")) {
            event.setCancelled(true);
        }
    }
}
