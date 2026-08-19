package dev.superseller.gifty.economy;

import java.util.logging.Logger;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Vault economy integration. Disabled gracefully when Vault (or an economy
 * provider) is not installed.
 */
public final class EconomyHook {

    private final JavaPlugin plugin;
    private final Logger logger;
    private Economy economy;
    private boolean enabled = false;
    private String providerName = "none";

    public EconomyHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * @param mode "auto" (default), "true" or "false" from config.yml
     */
    public void init(String mode) {
        if ("false".equalsIgnoreCase(mode)) {
            logger.info("Economy integration disabled by config.");
            return;
        }
        boolean force = "true".equalsIgnoreCase(mode);
        try {
            if (Class.forName("net.milkbowl.vault.economy.Economy") == null) {
                if (force) {
                    logger.warning("economy.enabled=true but Vault is missing!");
                }
                return;
            }
            RegisteredServiceProvider<Economy> rsp =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp == null || rsp.getProvider() == null) {
                if (force) {
                    logger.warning("economy.enabled=true but no Vault economy provider is registered!");
                }
                return;
            }
            economy = rsp.getProvider();
            enabled = economy.isEnabled();
            providerName = economy.getName();
            logger.info("Economy hooked: " + providerName);
        } catch (Throwable t) {
            enabled = false;
            if (force) {
                logger.warning("Could not hook economy: " + t.getMessage());
            } else {
                logger.info("Vault not found — money features disabled.");
            }
        }
    }

    public boolean isEnabled() {
        return enabled && economy != null;
    }

    public String providerName() {
        return providerName;
    }

    public double balance(OfflinePlayer player) {
        if (!isEnabled()) {
            return 0d;
        }
        try {
            return economy.getBalance(player);
        } catch (Throwable t) {
            return 0d;
        }
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!isEnabled()) {
            return false;
        }
        try {
            return economy.withdrawPlayer(player, amount).transactionSuccess();
        } catch (Throwable t) {
            logger.warning("Economy withdraw failed: " + t.getMessage());
            return false;
        }
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (!isEnabled()) {
            return false;
        }
        try {
            return economy.depositPlayer(player, amount).transactionSuccess();
        } catch (Throwable t) {
            logger.warning("Economy deposit failed: " + t.getMessage());
            return false;
        }
    }

    public String format(double amount) {
        if (!isEnabled()) {
            return String.format("$%.2f", amount);
        }
        try {
            return economy.format(amount);
        } catch (Throwable t) {
            return String.format("$%.2f", amount);
        }
    }
}
