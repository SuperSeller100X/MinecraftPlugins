package dev.superseller.gifty.papi;

import dev.superseller.gifty.GuiSessionManager;
import dev.superseller.gifty.storage.FileStorage;
import dev.superseller.gifty.util.Numbers;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI expansion.
 *   %gifty_pending%         pending deliveries
 *   %gifty_total_sent%      gifts sent (all time)
 *   %gifty_total_received%  gifts received (all time)
 *   %gifty_cooldown_left%   seconds until the player can send again
 */
public final class GiftyExpansion extends PlaceholderExpansion {

    private final FileStorage storage;
    private final GuiSessionManager sessions;

    public GiftyExpansion(FileStorage storage, GuiSessionManager sessions) {
        this.storage = storage;
        this.sessions = sessions;
    }

    @Override
    public String getIdentifier() {
        return "gifty";
    }

    @Override
    public String getAuthor() {
        return "SuperSeller100X";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return null;
        }
        switch (params.toLowerCase()) {
            case "pending":
                return String.valueOf(sessions.pendingCount(player.getUniqueId()));
            case "total_sent":
                return Numbers.format(storage.totalSent(player.getUniqueId()));
            case "total_received":
                return Numbers.format(storage.totalReceived(player.getUniqueId()));
            case "cooldown_left":
                return String.valueOf(sessions.cooldownLeft(player.getUniqueId()));
            default:
                return null;
        }
    }
}
