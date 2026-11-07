package dev.superseller.veltryx.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Player/OfflinePlayer name lookups shared by the commands. */
public final class Players {

    private Players() {
    }

    /** Online exact-name match first, then an offline player that is known. */
    @SuppressWarnings("deprecation")
    public static OfflinePlayer offline(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() || offline.isOnline() ? offline : null;
    }

    /** Best-known display name for an offline player. */
    public static String name(OfflinePlayer player, String fallback) {
        return player.getName() == null ? fallback : player.getName();
    }
}
