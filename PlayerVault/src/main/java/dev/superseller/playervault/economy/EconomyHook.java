package dev.superseller.playervault.economy;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import dev.superseller.playervault.util.Numbers;

/**
 * Optional economy bridge for Vault and VaultUnlocked.
 *
 * <p>Both APIs are reached through reflection so the plugin compiles and loads with
 * neither of them installed. That also means PlayerVault works with <em>any</em>
 * economy plugin that registers itself with Vault — EssentialsX, CMI, Xconomy,
 * Gringotts, TreasureChest2 and friends — without listing each one as a dependency.
 *
 * <p>Lookup order: Vault first ({@code net.milkbowl.vault.economy.Economy}), then
 * VaultUnlocked ({@code net.milkbowl.vault2.economy.Economy}). If neither is
 * registered, upgrades simply report that no economy is available.
 */
public final class EconomyHook {

    private final Logger logger;

    private Object vault;
    private Method vaultBalance;
    private Method vaultHas;
    private Method vaultWithdraw;
    private Method vaultDeposit;
    private Method vaultFormat;

    private Object unlocked;
    private Method unlockedBalance;
    private Method unlockedHas;
    private Method unlockedWithdraw;
    private Method unlockedFormat;

    private boolean enabled;
    private String providerName = "none";

    public EconomyHook(JavaPlugin plugin) {
        this.logger = plugin.getLogger();
    }

    /** (Re)binds to whatever economy provider is currently registered. */
    public void init(boolean announce) {
        enabled = false;
        vault = null;
        unlocked = null;
        providerName = "none";
        if (hookVault() || hookUnlocked()) {
            return;
        }
        if (announce) {
            logger.info("No Vault / VaultUnlocked provider found — vault upgrades will be free.");
        }
    }

    private boolean hookVault() {
        try {
            Class<?> type = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(type);
            if (registration == null || registration.getProvider() == null) {
                return false;
            }
            Object provider = registration.getProvider();
            Method isEnabled = find(provider.getClass(), "isEnabled");
            if (isEnabled != null && Boolean.FALSE.equals(invoke(isEnabled, provider))) {
                return false;
            }
            Method balance = find(provider.getClass(), "getBalance", OfflinePlayer.class);
            Method withdraw = find(provider.getClass(), "withdrawPlayer", OfflinePlayer.class, double.class);
            if (balance == null || withdraw == null) {
                return false;
            }
            vault = provider;
            vaultBalance = balance;
            vaultWithdraw = withdraw;
            vaultHas = find(provider.getClass(), "has", OfflinePlayer.class, double.class);
            vaultDeposit = find(provider.getClass(), "depositPlayer", OfflinePlayer.class, double.class);
            vaultFormat = find(provider.getClass(), "format", double.class);
            Method name = find(provider.getClass(), "getName");
            providerName = name == null ? "Vault" : String.valueOf(invoke(name, provider));
            enabled = true;
            logger.info("Economy hooked via Vault: " + providerName);
            return true;
        } catch (Throwable ignored) {
            vault = null;
            return false;
        }
    }

    private boolean hookUnlocked() {
        try {
            Class<?> type = Class.forName("net.milkbowl.vault2.economy.Economy");
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(type);
            if (registration == null || registration.getProvider() == null) {
                return false;
            }
            Object provider = registration.getProvider();
            Method balance = find(provider.getClass(), "getBalance", UUID.class);
            Method withdraw = find(provider.getClass(), "withdraw", UUID.class, double.class);
            if (withdraw == null) {
                withdraw = find(provider.getClass(), "withdrawPlayer", UUID.class, double.class);
            }
            if (balance == null || withdraw == null) {
                return false;
            }
            unlocked = provider;
            unlockedBalance = balance;
            unlockedWithdraw = withdraw;
            unlockedHas = find(provider.getClass(), "has", UUID.class, double.class);
            unlockedFormat = find(provider.getClass(), "format", double.class);
            Method name = find(provider.getClass(), "getName");
            providerName = name == null ? "VaultUnlocked" : String.valueOf(invoke(name, provider));
            enabled = true;
            logger.info("Economy hooked via VaultUnlocked: " + providerName);
            return true;
        } catch (Throwable ignored) {
            unlocked = null;
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String providerName() {
        return providerName;
    }

    public boolean has(UUID owner, double amount) {
        if (amount <= 0.0d) {
            return true;
        }
        if (!enabled || owner == null) {
            return false;
        }
        try {
            if (vault != null && vaultHas != null) {
                return Boolean.TRUE.equals(invoke(vaultHas, vault, offline(owner), amount));
            }
            if (unlocked != null && unlockedHas != null) {
                return Boolean.TRUE.equals(invoke(unlockedHas, unlocked, owner, amount));
            }
            return balance(owner) + 1.0e-9d >= amount;
        } catch (RuntimeException ex) {
            logger.warning("Economy has() failed: " + ex.getMessage());
            return false;
        }
    }

    public double balance(UUID owner) {
        if (!enabled || owner == null) {
            return 0.0d;
        }
        try {
            if (vault != null && vaultBalance != null) {
                return number(invoke(vaultBalance, vault, offline(owner)));
            }
            if (unlocked != null && unlockedBalance != null) {
                return number(invoke(unlockedBalance, unlocked, owner));
            }
        } catch (RuntimeException ex) {
            logger.warning("Economy balance() failed: " + ex.getMessage());
        }
        return 0.0d;
    }

    public boolean withdraw(UUID owner, double amount) {
        if (amount <= 0.0d) {
            return true;
        }
        if (!enabled || owner == null) {
            return false;
        }
        try {
            if (vault != null && vaultWithdraw != null) {
                return success(invoke(vaultWithdraw, vault, offline(owner), amount));
            }
            if (unlocked != null && unlockedWithdraw != null) {
                return success(invoke(unlockedWithdraw, unlocked, owner, amount));
            }
        } catch (RuntimeException ex) {
            logger.warning("Economy withdraw() failed: " + ex.getMessage());
        }
        return false;
    }

    public boolean deposit(UUID owner, double amount) {
        if (amount <= 0.0d) {
            return true;
        }
        if (!enabled || owner == null) {
            return false;
        }
        try {
            if (vault != null && vaultDeposit != null) {
                return success(invoke(vaultDeposit, vault, offline(owner), amount));
            }
        } catch (RuntimeException ex) {
            logger.warning("Economy deposit() failed: " + ex.getMessage());
        }
        return false;
    }

    /** Formats an amount using the provider, falling back to a plain number. */
    public String format(double amount) {
        try {
            if (vault != null && vaultFormat != null) {
                Object value = invoke(vaultFormat, vault, amount);
                if (value != null) {
                    return String.valueOf(value);
                }
            }
            if (unlocked != null && unlockedFormat != null) {
                Object value = invoke(unlockedFormat, unlocked, amount);
                if (value != null) {
                    return String.valueOf(value);
                }
            }
        } catch (RuntimeException ignored) {
            // fall through to the plain formatter
        }
        return Numbers.pretty(amount);
    }

    private static OfflinePlayer offline(UUID owner) {
        return Bukkit.getOfflinePlayer(owner);
    }

    private static double number(Object value) {
        return value instanceof Number amount ? amount.doubleValue() : 0.0d;
    }

    /**
     * Interprets an economy response. Vault returns {@code EconomyResponse}; some
     * providers return a plain boolean, so both shapes are accepted.
     */
    private static boolean success(Object response) {
        if (response instanceof Boolean flag) {
            return flag;
        }
        if (response == null) {
            return false;
        }
        try {
            Method method = response.getClass().getMethod("transactionSuccess");
            return Boolean.TRUE.equals(method.invoke(response));
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Method find(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getMethod(name, parameters);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }
}
