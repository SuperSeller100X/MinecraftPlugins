package dev.superseller.subscriptions.economy;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Talks to almost any economy plugin through Vault, then VaultUnlocked
 * ({@code net.milkbowl.vault2.economy.Economy}) via reflection. Disabled
 * cleanly when neither bridge (nor a provider) is installed.
 */
public final class EconomyService {

    private final JavaPlugin plugin;
    private final Logger logger;

    private Economy vault;
    private Object unlocked;
    private Method unlockedBalance;
    private Method unlockedWithdraw;
    private Method unlockedDeposit;
    private Method unlockedFormat;
    private Method unlockedHas;
    private boolean enabled;
    private String providerName = "none";

    public EconomyService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void init(String mode) {
        enabled = false;
        vault = null;
        unlocked = null;
        providerName = "none";
        if ("false".equalsIgnoreCase(mode)) {
            logger.info("Economy integration disabled by config.");
            return;
        }
        boolean force = "true".equalsIgnoreCase(mode);
        if (hookVault()) {
            return;
        }
        if (hookUnlocked()) {
            return;
        }
        if (force) {
            logger.warning("economy.enabled=true but no Vault / VaultUnlocked provider is registered.");
        } else {
            logger.info("No economy bridge found — money features disabled.");
        }
    }

    private boolean hookVault() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null
                    && Bukkit.getServicesManager().getRegistration(Economy.class) == null) {
                return false;
            }
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp == null || rsp.getProvider() == null) {
                return false;
            }
            vault = rsp.getProvider();
            enabled = vault.isEnabled();
            providerName = vault.getName();
            if (enabled) {
                logger.info("Economy hooked via Vault: " + providerName);
            }
            return enabled;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean hookUnlocked() {
        try {
            Class<?> type = Class.forName("net.milkbowl.vault2.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(type);
            if (rsp == null || rsp.getProvider() == null) {
                return false;
            }
            unlocked = rsp.getProvider();
            unlockedBalance = find(unlocked.getClass(), "getBalance", UUID.class);
            unlockedHas = find(unlocked.getClass(), "has", UUID.class, double.class);
            unlockedWithdraw = find(unlocked.getClass(), "withdraw", UUID.class, double.class);
            if (unlockedWithdraw == null) {
                unlockedWithdraw = find(unlocked.getClass(), "withdrawPlayer", UUID.class, double.class);
            }
            unlockedDeposit = find(unlocked.getClass(), "deposit", UUID.class, double.class);
            if (unlockedDeposit == null) {
                unlockedDeposit = find(unlocked.getClass(), "depositPlayer", UUID.class, double.class);
            }
            unlockedFormat = find(unlocked.getClass(), "format", double.class);
            Method name = find(unlocked.getClass(), "getName");
            providerName = name == null ? "VaultUnlocked" : String.valueOf(name.invoke(unlocked));
            enabled = unlockedBalance != null;
            if (enabled) {
                logger.info("Economy hooked via VaultUnlocked: " + providerName);
            }
            return enabled;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String providerName() {
        return providerName;
    }

    public double balance(UUID uuid) {
        if (!enabled || uuid == null) {
            return 0d;
        }
        try {
            if (vault != null) {
                return vault.getBalance(player(uuid));
            }
            if (unlockedBalance != null) {
                Object value = unlockedBalance.invoke(unlocked, uuid);
                if (value instanceof Number number) {
                    return number.doubleValue();
                }
            }
        } catch (Throwable t) {
            logger.warning("Balance lookup failed: " + t.getMessage());
        }
        return 0d;
    }

    public boolean has(UUID uuid, double amount) {
        if (amount <= 0d) {
            return true;
        }
        if (!enabled || uuid == null) {
            return false;
        }
        try {
            if (vault != null) {
                return vault.has(player(uuid), amount);
            }
            if (unlockedHas != null) {
                Object value = unlockedHas.invoke(unlocked, uuid, amount);
                if (value instanceof Boolean bool) {
                    return bool;
                }
            }
            return balance(uuid) + 1e-9 >= amount;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean withdraw(UUID uuid, double amount) {
        if (amount <= 0d) {
            return true;
        }
        if (!enabled || uuid == null) {
            return false;
        }
        try {
            if (vault != null) {
                return vault.withdrawPlayer(player(uuid), amount).transactionSuccess();
            }
            if (unlockedWithdraw != null) {
                return transactionOk(unlockedWithdraw.invoke(unlocked, uuid, amount));
            }
        } catch (Throwable t) {
            logger.warning("Withdraw failed: " + t.getMessage());
        }
        return false;
    }

    public boolean deposit(UUID uuid, double amount) {
        if (amount <= 0d) {
            return true;
        }
        if (!enabled || uuid == null) {
            return false;
        }
        try {
            if (vault != null) {
                return vault.depositPlayer(player(uuid), amount).transactionSuccess();
            }
            if (unlockedDeposit != null) {
                return transactionOk(unlockedDeposit.invoke(unlocked, uuid, amount));
            }
        } catch (Throwable t) {
            logger.warning("Deposit failed: " + t.getMessage());
        }
        return false;
    }

    public String format(double amount) {
        try {
            if (vault != null) {
                return vault.format(amount);
            }
            if (unlockedFormat != null) {
                Object value = unlockedFormat.invoke(unlocked, amount);
                if (value != null) {
                    return String.valueOf(value);
                }
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return "$" + dev.superseller.subscriptions.util.Numbers.compact(amount);
    }

    private OfflinePlayer player(UUID uuid) {
        return Bukkit.getOfflinePlayer(uuid);
    }

    private static boolean transactionOk(Object response) {
        if (response instanceof Boolean bool) {
            return bool;
        }
        if (response == null) {
            return false;
        }
        try {
            Method method = response.getClass().getMethod("transactionSuccess");
            Object value = method.invoke(response);
            return value instanceof Boolean bool && bool;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static Method find(Class<?> type, String name, Class<?>... args) {
        try {
            return type.getMethod(name, args);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
