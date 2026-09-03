package dev.superseller.combattag.combat;

import dev.superseller.combattag.config.PluginConfig;
import org.bukkit.entity.Player;

/**
 * Single decision point for every bypass in CombatTag.
 *
 * <p>Every check is evaluated <b>live, per event</b> against the current in-memory
 * configuration, so switching a bypass off makes all restrictions fully functional
 * immediately — no {@code /cta reload}, no server restart, not even a re-login.</p>
 *
 * <p>The master switch {@code bypass.enabled} wins over everything: while it is
 * {@code false}, no permission, no OP status and no runtime grant can bypass anything.</p>
 */
public final class BypassService {

    private final PluginConfig config;

    public BypassService(PluginConfig config) {
        this.config = config;
    }

    /**
     * May this player bypass the given restriction right now?
     *
     * @param player the player, may be {@code null}
     * @param action the restriction being evaluated
     * @return {@code true} only when bypasses are globally enabled <em>and</em> this
     *         specific bypass is enabled <em>and</em> the player is entitled to it
     */
    public boolean canBypass(Player player, BlockedAction action) {
        if (player == null || action == null) {
            return false;
        }
        if (!config.isBypassEnabled()) {
            return false; // Master switch off -> everything is enforced.
        }
        if (!config.isBypassAllowed(bypassKey(action))) {
            return false; // This individual bypass is switched off.
        }
        if (config.isOpBypasses() && player.isOp()) {
            return true;
        }
        if (!config.isPermissionBypassEnabled()) {
            return false;
        }
        return player.hasPermission(action.bypassPermission())
                || player.hasPermission("combattag.bypass.*");
    }

    /** Ender pearls and chorus fruit share the teleport bypass toggle. */
    private static String bypassKey(BlockedAction action) {
        return action == BlockedAction.PEARL ? BlockedAction.TELEPORT.id() : action.id();
    }

    /** @return true when this player must never be combat tagged in the first place. */
    public boolean canBypassTagging(Player player) {
        if (player == null || !config.isBypassEnabled() || !config.isBypassAllowed("tag")) {
            return false;
        }
        if (config.isOpBypasses() && player.isOp()) {
            return true;
        }
        return config.isPermissionBypassEnabled()
                && (player.hasPermission("combattag.bypass.tag")
                    || player.hasPermission("combattag.bypass.*"));
    }

    /** @return true when this player may quit while tagged without being punished. */
    public boolean canBypassCombatLog(Player player) {
        if (player == null || !config.isBypassEnabled() || !config.isBypassAllowed("combatlog")) {
            return false;
        }
        if (config.isOpBypasses() && player.isOp()) {
            return true;
        }
        return config.isPermissionBypassEnabled()
                && (player.hasPermission("combattag.bypass.combatlog")
                    || player.hasPermission("combattag.bypass.*"));
    }
}
