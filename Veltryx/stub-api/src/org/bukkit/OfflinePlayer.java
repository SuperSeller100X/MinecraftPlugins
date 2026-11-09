package org.bukkit;

import java.util.UUID;

public interface OfflinePlayer {
    UUID getUniqueId();
    String getName();
    boolean isOnline();
    boolean hasPlayedBefore();
    default void incrementStatistic(Statistic statistic, Material material) {}
}
