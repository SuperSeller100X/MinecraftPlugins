package dev.superseller.shardtools.economy;

import java.lang.reflect.Method;
import java.util.Locale;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reflection bridge to whatever economy Vault exposes (EssentialsX Economy,
 * CMI, ...). There is no compile-time Vault dependency: when Vault or an
 * economy provider is missing, {@link #available()} simply returns false and
 * /st buy explains what to install.
 */
public final class VaultBridge {

    private final JavaPlugin plugin;
    private Object economy;
    private Method getBalance;
    private Method withdrawPlayer;
    private Method depositPlayer;
    private Method format;
    private Method transactionSuccess;
    private boolean initialized;

    public VaultBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** True when Vault and an economy provider (e.g. EssentialsX) are present. */
    public boolean available() {
        if (!initialized) {
            initialized = true;
            connect();
        }
        return economy != null;
    }

    /** Re-checks for Vault (used by /st reload so admins can hot-plug it). */
    public void reconnect() {
        economy = null;
        getBalance = null;
        withdrawPlayer = null;
        depositPlayer = null;
        format = null;
        transactionSuccess = null;
        initialized = false;
        available();
    }

    private void connect() {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
                return;
            }
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object registration = plugin.getServer().getServicesManager().getRegistration(economyClass);
            if (registration == null) {
                return;
            }
            Object provider = registration.getClass().getMethod("getProvider").invoke(registration);
            if (provider == null) {
                return;
            }
            getBalance = economyClass.getMethod("getBalance", OfflinePlayer.class);
            withdrawPlayer = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            depositPlayer = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            transactionSuccess = Class.forName("net.milkbowl.vault.economy.EconomyResponse")
                    .getMethod("transactionSuccess");
            try {
                format = economyClass.getMethod("format", double.class);
            } catch (NoSuchMethodException ignored) {
                // formatting falls back to a plain number
            }
            economy = provider;
            String name = "?";
            try {
                name = String.valueOf(economyClass.getMethod("getName").invoke(provider));
            } catch (Throwable ignored) {
                // keep "?"
            }
            plugin.getLogger().info("Vault economy connected: " + name);
        } catch (Throwable error) {
            plugin.getLogger().warning("Vault economy not available: " + error.getMessage());
        }
    }

    /** Money balance, or -1 when the lookup fails. */
    public double balance(OfflinePlayer player) {
        try {
            return (Double) getBalance.invoke(economy, player);
        } catch (Throwable error) {
            return -1.0D;
        }
    }

    /** Withdraws money; true only when the economy confirms the transaction. */
    public boolean withdraw(OfflinePlayer player, double amount) {
        try {
            Object response = withdrawPlayer.invoke(economy, player, amount);
            return (Boolean) transactionSuccess.invoke(response);
        } catch (Throwable error) {
            return false;
        }
    }

    /** Deposits money; true only when the economy confirms the transaction. */
    public boolean deposit(OfflinePlayer player, double amount) {
        try {
            Object response = depositPlayer.invoke(economy, player, amount);
            return (Boolean) transactionSuccess.invoke(response);
        } catch (Throwable error) {
            return false;
        }
    }

    /** Pretty money string via the economy plugin when possible. */
    public String format(double amount) {
        if (format != null) {
            try {
                return String.valueOf(format.invoke(economy, amount));
            } catch (Throwable ignored) {
                // fall through
            }
        }
        return String.format(Locale.ROOT, "%,.2f", amount);
    }
}
