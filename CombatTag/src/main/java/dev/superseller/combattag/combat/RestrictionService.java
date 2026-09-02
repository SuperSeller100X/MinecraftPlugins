package dev.superseller.combattag.combat;

import dev.superseller.combattag.config.Messages;
import dev.superseller.combattag.config.PluginConfig;
import dev.superseller.combattag.util.SoundUtil;
import dev.superseller.combattag.util.TimeUtil;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Decides whether a tagged player may perform a restricted action and delivers the
 * denial feedback (message, sound, admin notification, statistics).
 */
public final class RestrictionService {

    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final Messages messages;
    private final CombatManager combat;
    private final BypassService bypass;

    public RestrictionService(JavaPlugin plugin, PluginConfig config, Messages messages,
                              CombatManager combat, BypassService bypass) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.combat = combat;
        this.bypass = bypass;
    }

    /**
     * @return {@code true} when the action must be cancelled (player is tagged and has no bypass)
     */
    public boolean shouldBlock(Player player, BlockedAction action) {
        if (player == null || action == null) {
            return false;
        }
        if (!combat.isTagged(player.getUniqueId())) {
            return false;
        }
        // Evaluated live on every event: as soon as a bypass is switched off, the
        // restriction is enforced again without any reload.
        return !bypass.canBypass(player, action);
    }

    /**
     * Checks the action and, when blocked, sends the configured feedback.
     *
     * @return {@code true} when the caller must cancel the action
     */
    public boolean denyIfBlocked(Player player, BlockedAction action, String detail) {
        if (!shouldBlock(player, action)) {
            return false;
        }
        combat.countBlocked();

        String seconds = String.valueOf(combat.remainingSeconds(player.getUniqueId()));
        String pretty = TimeUtil.format(combat.get(player.getUniqueId())
                .map(e -> e.remainingMillis(System.currentTimeMillis()))
                .orElse(0L));

        messages.send(player, "blocked." + action.id(),
                "seconds", seconds,
                "time", pretty,
                "detail", detail == null ? "" : detail);
        SoundUtil.play(player, config.getSoundBlocked(), config.isSoundsEnabled());

        if (config.isNotifyAdminsOnBlock()) {
            notifyAdmins(player, action, detail);
        }
        return true;
    }

    private void notifyAdmins(Player player, BlockedAction action, String detail) {
        Component component = messages.get("admin.block-notification", java.util.Map.of(
                "player", player.getName(),
                "action", action.id(),
                "detail", detail == null ? "" : detail));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("combattag.notify")) {
                online.sendMessage(component);
            }
        }
        plugin.getLogger().fine(() -> "Blocked " + action.id() + " for " + player.getName());
    }

    /** Matches a teleport cause name against the configured allow-list. */
    public boolean isTeleportCauseAllowed(String causeName) {
        if (causeName == null) {
            return false;
        }
        return config.getAllowedTeleportCauses().contains(causeName.toUpperCase(Locale.ROOT));
    }
}
