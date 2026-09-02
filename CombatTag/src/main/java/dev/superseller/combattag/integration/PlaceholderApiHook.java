package dev.superseller.combattag.integration;

import dev.superseller.combattag.CombatTagPlugin;
import dev.superseller.combattag.combat.CombatManager;
import dev.superseller.combattag.util.TimeUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI expansion.
 *
 * <ul>
 *   <li>{@code %combattag_tagged%} — true / false</li>
 *   <li>{@code %combattag_status%} — In Combat / Safe</li>
 *   <li>{@code %combattag_seconds%} — remaining whole seconds</li>
 *   <li>{@code %combattag_time%} — pretty remaining time</li>
 *   <li>{@code %combattag_active%} — number of tagged players</li>
 * </ul>
 */
public final class PlaceholderApiHook extends PlaceholderExpansion {

    private final CombatTagPlugin plugin;
    private final CombatManager combat;

    public PlaceholderApiHook(CombatTagPlugin plugin, CombatManager combat) {
        this.plugin = plugin;
        this.combat = combat;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "combattag";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SuperSeller100X";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.equalsIgnoreCase("active")) {
            return String.valueOf(combat.getActiveCount());
        }
        if (player == null) {
            return "";
        }
        boolean tagged = combat.isTagged(player.getUniqueId());
        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "tagged" -> String.valueOf(tagged);
            case "status" -> tagged ? "In Combat" : "Safe";
            case "seconds" -> String.valueOf(combat.remainingSeconds(player.getUniqueId()));
            case "time" -> combat.get(player.getUniqueId())
                    .map(e -> TimeUtil.format(e.remainingMillis(System.currentTimeMillis())))
                    .orElse("0.0s");
            default -> null;
        };
    }
}
