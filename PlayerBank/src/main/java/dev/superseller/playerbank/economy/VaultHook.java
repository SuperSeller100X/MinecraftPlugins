package dev.superseller.playerbank.economy;

import dev.superseller.playerbank.PlayerBankPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Wallet (normal economy) only. Bank balances never go through Vault,
 * so other plugins cannot spend banked money.
 */
public final class VaultHook {

    private final PlayerBankPlugin plugin;
    private Economy economy;
    private boolean enabled;

    public VaultHook(PlayerBankPlugin plugin) {
        this.plugin = plugin;
    }

    public void hook() {
        enabled = false;
        economy = null;
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
                plugin.getLogger().warning("Vault not found.");
                return;
            }
            RegisteredServiceProvider<Economy> rsp =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp == null || rsp.getProvider() == null) {
                plugin.getLogger().warning("No Vault economy provider registered.");
                return;
            }
            economy = rsp.getProvider();
            enabled = economy.isEnabled();
            plugin.getLogger().info("Hooked wallet economy: " + economy.getName());
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not hook Vault: " + t.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled && economy != null;
    }

    public double wallet(OfflinePlayer player) {
        if (!isEnabled()) {
            return 0;
        }
        return economy.getBalance(player);
    }

    public boolean takeWallet(OfflinePlayer player, double amount) {
        if (!isEnabled()) {
            return false;
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean giveWallet(OfflinePlayer player, double amount) {
        if (!isEnabled()) {
            return false;
        }
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public String format(double amount) {
        if (!isEnabled()) {
            return String.format("%.2f", amount);
        }
        try {
            return economy.format(amount);
        } catch (Throwable t) {
            return String.format("%.2f", amount);
        }
    }
}
