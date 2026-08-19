package org.bukkit;

import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public interface OfflinePlayer {
    UUID getUniqueId();
    String getName();
    boolean isOnline();
    Player getPlayer();
    boolean hasPlayedBefore();
}
