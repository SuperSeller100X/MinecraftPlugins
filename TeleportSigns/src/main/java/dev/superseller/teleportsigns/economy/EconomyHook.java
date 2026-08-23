package dev.superseller.teleportsigns.economy;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Optional Vault / VaultUnlocked hook via reflection so the plugin compiles
 * without those APIs on the classpath.
 */
public final class EconomyHook {

    private final Logger logger;

    private Object vault;
    private Method vaultBalance;
    private Method vaultHas;
    private Method vaultWithdraw;
    private Method vaultDeposit;
    private Method vaultFormat;
    private Method vaultEnabled;

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

    public void init(String mode) {
        enabled = false;
        vault = null;
        unlocked = null;
        providerName = "none";
        if (mode != null && "false".equalsIgnoreCase(mode.trim())) {
            logger.info("Economy integration disabled by config.");
            return;
        }
        boolean force = mode != null && "true".equalsIgnoreCase(mode.trim());
        if (hookVault()) {
            return;
        }
        if (hookUnlocked()) {
            return;
        }
        if (force) {
            logger.warning("economy.enabled=true but no Vault / VaultUnlocked provider is registered.");
        } else {
            logger.info("No economy bridge found — teleport costs are skipped.");
        }
    }

    private boolean hookVault() {
        try {
            Class<?> type = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(type);
            if (rsp == null || rsp.getProvider() == null) {
                return false;
            }
            vault = rsp.getProvider();
            vaultEnabled = find(vault.getClass(), "isEnabled");
            if (vaultEnabled != null) {
                Object value = vaultEnabled.invoke(vault);
                if (value instanceof Boolean && !((Boolean) value).booleanValue()) {
                    vault = null;
                    return false;
                }
            }
            vaultBalance = find(vault.getClass(), "getBalance", OfflinePlayer.class);
            vaultHas = find(vault.getClass(), "has", OfflinePlayer.class, double.class);
            vaultWithdraw = find(vault.getClass(), "withdrawPlayer", OfflinePlayer.class, double.class);
            vaultDeposit = find(vault.getClass(), "depositPlayer", OfflinePlayer.class, double.class);
            vaultFormat = find(vault.getClass(), "format", double.class);
            Method name = find(vault.getClass(), "getName");
            providerName = name == null ? "Vault" : String.valueOf(name.invoke(vault));
            enabled = vaultBalance != null && vaultWithdraw != null;
            if (enabled) {
                logger.info("Economy hooked via Vault: " + providerName);
            }
            return enabled;
        } catch (Throwable ignored) {
            vault = null;
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
            unlockedFormat = find(unlocked.getClass(), "format", double.class);
            Method name = find(unlocked.getClass(), "getName");
            providerName = name == null ? "VaultUnlocked" : String.valueOf(name.invoke(unlocked));
            enabled = unlockedBalance != null && unlockedWithdraw != null;
            if (enabled) {
                logger.info("Economy hooked via VaultUnlocked: " + providerName);
            }
            return enabled;
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

    public boolean has(UUID uuid, double amount) {
        if (amount <= 0.0d) {
            return true;
        }
        if (!enabled || uuid == null) {
            return false;
        }
        try {
            if (vault != null && vaultHas != null) {
                Object value = vaultHas.invoke(vault, player(uuid), amount);
                return value instanceof Boolean && ((Boolean) value).booleanValue();
            }
            if (unlocked != null && unlockedHas != null) {
                Object value = unlockedHas.invoke(unlocked, uuid, amount);
                return value instanceof Boolean && ((Boolean) value).booleanValue();
            }
            return balance(uuid) + 1.0e-9d >= amount;
        } catch (Throwable t) {
            logger.warning("Economy has() failed: " + t.getMessage());
            return false;
        }
    }

    public double balance(UUID uuid) {
        if (!enabled || uuid == null) {
            return 0.0d;
        }
        try {
            if (vault != null && vaultBalance != null) {
                Object value = vaultBalance.invoke(vault, player(uuid));
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
            }
            if (unlocked != null && unlockedBalance != null) {
                Object value = unlockedBalance.invoke(unlocked, uuid);
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
            }
        } catch (Throwable t) {
            logger.warning("Economy balance failed: " + t.getMessage());
        }
        return 0.0d;
    }

    public boolean withdraw(UUID uuid, double amount) {
        if (amount <= 0.0d) {
            return true;
        }
        if (!enabled || uuid == null) {
            return false;
        }
        try {
            if (vault != null && vaultWithdraw != null) {
                return transactionOk(vaultWithdraw.invoke(vault, player(uuid), amount));
            }
            if (unlocked != null && unlockedWithdraw != null) {
                return transactionOk(unlockedWithdraw.invoke(unlocked, uuid, amount));
            }
        } catch (Throwable t) {
            logger.warning("Economy withdraw failed: " + t.getMessage());
        }
        return false;
    }

    public boolean deposit(UUID uuid, double amount) {
        if (amount <= 0.0d) {
            return true;
        }
        if (!enabled || uuid == null) {
            return false;
        }
        try {
            Method deposit = vaultDeposit;
            if (vault != null && deposit != null) {
                return transactionOk(deposit.invoke(vault, player(uuid), amount));
            }
        } catch (Throwable t) {
            logger.warning("Economy deposit failed: " + t.getMessage());
        }
        return false;
    }

    public String format(double amount) {
        try {
            if (vault != null && vaultFormat != null) {
                Object value = vaultFormat.invoke(vault, amount);
                if (value != null) {
                    return String.valueOf(value);
                }
            }
            if (unlocked != null && unlockedFormat != null) {
                Object value = unlockedFormat.invoke(unlocked, amount);
                if (value != null) {
                    return String.valueOf(value);
                }
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return dev.superseller.teleportsigns.util.Numbers.pretty(amount);
    }

    private OfflinePlayer player(UUID uuid) {
        return Bukkit.getOfflinePlayer(uuid);
    }

    private static boolean transactionOk(Object response) {
        if (response instanceof Boolean) {
            return ((Boolean) response).booleanValue();
        }
        if (response == null) {
            return false;
        }
        try {
            Method method = response.getClass().getMethod("transactionSuccess");
            Object value = method.invoke(response);
            return value instanceof Boolean && ((Boolean) value).booleanValue();
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
