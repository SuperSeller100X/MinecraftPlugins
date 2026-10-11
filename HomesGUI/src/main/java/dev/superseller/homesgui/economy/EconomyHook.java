package dev.superseller.homesgui.economy;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Optional reflective bridge to Vault and VaultUnlocked. Reflection keeps HomesGUI
 * loadable when Vault is absent (including on Folia, where Vault may be unavailable).
 */
public final class EconomyHook {

    private final JavaPlugin plugin;
    private volatile Binding binding;

    public EconomyHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void reload(boolean enabled) {
        binding = null;
        if (!enabled) {
            plugin.getLogger().info("Economy integration is disabled by config.");
            return;
        }
        if (bind("net.milkbowl.vault.economy.Economy", false)
                || bind("net.milkbowl.vault2.economy.Economy", true)) {
            plugin.getLogger().info("HomesGUI economy hooked via " + binding.name() + ".");
        } else {
            plugin.getLogger().warning("No Vault economy provider is registered. Paid home purchases will fail closed.");
        }
    }

    public boolean available() {
        return binding != null;
    }

    public String providerName() {
        Binding current = binding;
        return current == null ? "none" : current.name();
    }

    public boolean has(Player player, double amount) {
        Binding current = binding;
        if (current == null || player == null || !Double.isFinite(amount) || amount < 0.0) {
            return false;
        }
        if (amount == 0.0) {
            return true;
        }
        try {
            if (current.has() != null) {
                return Boolean.TRUE.equals(invoke(current.has(), current.provider(), current.identity(player), amount));
            }
            return balance(current, player) + 1.0e-8 >= amount;
        } catch (RuntimeException exception) {
            warn("Could not query the economy provider", exception);
            return false;
        }
    }

    public boolean withdraw(Player player, double amount) {
        Binding current = binding;
        if (amount <= 0.0) {
            return true;
        }
        if (current == null || player == null || !Double.isFinite(amount)) {
            return false;
        }
        try {
            Object response = invoke(current.withdraw(), current.provider(), current.identity(player), amount);
            return transactionSucceeded(response);
        } catch (RuntimeException exception) {
            warn("Could not withdraw from the economy provider", exception);
            return false;
        }
    }

    public String format(double amount, String fallback) {
        Binding current = binding;
        if (current != null && current.format() != null) {
            try {
                Object formatted = invoke(current.format(), current.provider(), amount);
                if (formatted != null) {
                    return String.valueOf(formatted);
                }
            } catch (RuntimeException exception) {
                warn("Could not format currency using the economy provider", exception);
            }
        }
        return fallback;
    }

    private boolean bind(String className, boolean uuidIdentity) {
        try {
            Class<?> economyType = Class.forName(className, false, plugin.getClass().getClassLoader());
            @SuppressWarnings({"unchecked", "rawtypes"})
            RegisteredServiceProvider<?> registration = plugin.getServer().getServicesManager()
                    .getRegistration((Class) economyType);
            if (registration == null || registration.getProvider() == null) {
                return false;
            }
            Object provider = registration.getProvider();
            Class<?> identityType = uuidIdentity ? UUID.class : OfflinePlayer.class;
            Method balance = firstMethod(economyType, provider.getClass(), "getBalance", identityType);
            String[] withdrawNames = uuidIdentity ? new String[]{"withdraw", "withdrawPlayer"}
                    : new String[]{"withdrawPlayer", "withdraw"};
            Method withdraw = firstMethod(economyType, provider.getClass(), withdrawNames, identityType, double.class);
            if (balance == null || withdraw == null) {
                return false;
            }
            Method has = firstMethod(economyType, provider.getClass(), "has", identityType, double.class);
            Method format = firstMethod(economyType, provider.getClass(), "format", double.class);
            Method getName = firstMethod(economyType, provider.getClass(), "getName");
            String name = getName == null ? (uuidIdentity ? "VaultUnlocked" : "Vault")
                    : String.valueOf(invoke(getName, provider));
            binding = new Binding(provider, balance, has, withdraw, format, name, uuidIdentity);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (Throwable exception) {
            plugin.getLogger().log(Level.FINE, "Could not bind optional economy API " + className, exception);
            return false;
        }
    }

    private static Method firstMethod(Class<?> api, Class<?> implementation, String name, Class<?>... parameters) {
        return firstMethod(api, implementation, new String[]{name}, parameters);
    }

    private static Method firstMethod(Class<?> api, Class<?> implementation, String[] names,
                                      Class<?>... parameters) {
        for (String name : names) {
            try {
                return api.getMethod(name, parameters);
            } catch (NoSuchMethodException ignored) {
                try {
                    return implementation.getMethod(name, parameters);
                } catch (NoSuchMethodException ignoredAgain) {
                    // Try the next API spelling.
                }
            }
        }
        return null;
    }

    private double balance(Binding current, Player player) {
        Object amount = invoke(current.balance(), current.provider(), current.identity(player));
        return amount instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean transactionSucceeded(Object response) {
        if (response instanceof Boolean success) {
            return success;
        }
        if (response == null) {
            return false;
        }
        try {
            Method method = response.getClass().getMethod("transactionSuccess");
            return Boolean.TRUE.equals(method.invoke(response));
        } catch (ReflectiveOperationException ignored) {
            // Providers that follow the Vault contract return a successful EconomyResponse.
            return true;
        }
    }

    private void warn(String message, RuntimeException exception) {
        plugin.getLogger().log(Level.WARNING, message + ": " + exception.getMessage());
    }

    private record Binding(Object provider, Method balance, Method has, Method withdraw,
                           Method format, String name, boolean uuidIdentity) {
        Object identity(Player player) {
            return uuidIdentity ? player.getUniqueId() : (OfflinePlayer) player;
        }
    }
}
