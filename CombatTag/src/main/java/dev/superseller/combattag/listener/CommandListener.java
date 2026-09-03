package dev.superseller.combattag.listener;

import dev.superseller.combattag.combat.BlockedAction;
import dev.superseller.combattag.combat.CombatManager;
import dev.superseller.combattag.combat.RestrictionService;
import dev.superseller.combattag.config.PluginConfig;
import dev.superseller.combattag.util.CommandMatcher;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Blocks shop, teleport, EasyMending and blacklisted commands while the sender is tagged.
 * Runs at {@link EventPriority#LOWEST} so the command never reaches the target plugin.
 */
public final class CommandListener implements Listener {

    private final PluginConfig config;
    private final CombatManager combat;
    private final RestrictionService restrictions;

    public CommandListener(PluginConfig config, CombatManager combat, RestrictionService restrictions) {
        this.config = config;
        this.combat = combat;
        this.restrictions = restrictions;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!combat.isTagged(player.getUniqueId())) {
            return;
        }
        String message = event.getMessage();
        String label = CommandMatcher.label(message);

        // Always allow CombatTag's own commands so players can check their timer.
        if (label.startsWith("combattag") || label.equals("ct") || label.equals("combat")
                || label.startsWith("combattagadmin") || label.equals("cta")) {
            return;
        }

        if (config.isBlockShops() && CommandMatcher.matches(message, config.getShopCommands())
                && restrictions.denyIfBlocked(player, BlockedAction.SHOP, "/" + label)) {
            event.setCancelled(true);
            return;
        }
        if (config.isBlockTeleports() && CommandMatcher.matches(message, config.getTeleportCommands())
                && restrictions.denyIfBlocked(player, BlockedAction.TELEPORT, "/" + label)) {
            event.setCancelled(true);
            return;
        }
        if (config.isBlockEasyMending() && CommandMatcher.matches(message, config.getEasyMendingCommands())
                && restrictions.denyIfBlocked(player, BlockedAction.EASYMENDING, "/" + label)) {
            event.setCancelled(true);
            return;
        }
        if (!config.isBlockedCommandsEnabled()) {
            return;
        }
        boolean blocked = config.isCommandWhitelistMode()
                ? !CommandMatcher.matches(message, config.getAllowedCommands())
                : CommandMatcher.matches(message, config.getBlockedCommands());
        if (blocked && restrictions.denyIfBlocked(player, BlockedAction.COMMAND, "/" + label)) {
            event.setCancelled(true);
        }
    }
}
