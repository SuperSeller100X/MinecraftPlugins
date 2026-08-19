package net.milkbowl.vault.economy;

import org.bukkit.OfflinePlayer;

public interface Economy {
    String getName();

    boolean isEnabled();

    double getBalance(OfflinePlayer player);

    boolean has(OfflinePlayer player, double amount);

    EconomyResponse withdrawPlayer(OfflinePlayer player, double amount);

    EconomyResponse depositPlayer(OfflinePlayer player, double amount);

    String format(double amount);
}
