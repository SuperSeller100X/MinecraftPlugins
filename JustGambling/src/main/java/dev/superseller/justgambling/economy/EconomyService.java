package dev.superseller.justgambling.economy;

import dev.superseller.justgambling.config.PluginSettings;
import dev.superseller.justgambling.scheduler.PlatformScheduler;
import dev.superseller.justgambling.storage.GamblingStore;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Economy bridge. Vault is preferred, EssentialsX's native API is the direct
 * fallback, and a private YAML currency is the final fallback configured by
 * the server owner.
 */
public final class EconomyService implements Listener {
    private final JavaPlugin plugin;
    private final GamblingStore store;
    private final PluginSettings settings;
    private Economy vault;
    private EssentialsEconomy essentials;
    private String providerName = "none";
    private boolean registered;

    public EconomyService(JavaPlugin plugin, GamblingStore store, PluginSettings settings) {
        this.plugin = plugin;
        this.store = store;
        this.settings = settings;
    }

    public void start() {
        if (!registered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
        hook();
        PlatformScheduler.runGlobalDelayed(this::hook, 1L);
    }

    public void hook() {
        String old = providerName;
        vault = null;
        essentials = null;
        providerName = "none";

        if (!settings.economyMode().equals("internal") && hookVault()) {
            logChange(old);
            return;
        }
        if (!settings.economyMode().equals("internal") && hookEssentials()) {
            logChange(old);
            return;
        }
        if (!"none".equals(old)) {
            plugin.getLogger().warning("JustGambling lost its external economy provider; checking fallback currency.");
        }
    }

    public boolean hasExternalProvider() {
        return !settings.economyMode().equals("internal") && (vault != null || essentials != null);
    }

    public boolean available() {
        return hasExternalProvider() || (!settings.economyMode().equals("vault") && settings.fallbackEnabled());
    }

    public String providerName() {
        if (hasExternalProvider()) {
            return providerName;
        }
        return !settings.economyMode().equals("vault") && settings.fallbackEnabled()
                ? "JustGambling fallback (" + settings.fallbackCurrency() + ")" : "none";
    }

    public double balance(Player player) {
        return player == null ? 0.0 : balance(player.getUniqueId(), player);
    }

    public double balance(UUID uuid, OfflinePlayer offlinePlayer) {
        if (uuid == null) {
            return 0.0;
        }
        try {
            if (hasExternalProvider() && vault != null && offlinePlayer != null) {
                return finite(vault.getBalance(offlinePlayer));
            }
            if (hasExternalProvider() && essentials != null) {
                return finite(essentials.balance(uuid));
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Economy balance lookup failed: " + throwable.getMessage());
            return 0.0;
        }
        return fallbackUsable() ? store.fallbackBalance(uuid, offlinePlayer == null ? null : offlinePlayer.getName()) : 0.0;
    }

    public boolean withdraw(Player player, double amount) {
        if (player == null || !finitePositive(amount)) {
            return false;
        }
        try {
            if (hasExternalProvider() && vault != null) {
                return vault.withdrawPlayer(player, amount).transactionSuccess();
            }
            if (hasExternalProvider() && essentials != null) {
                return essentials.subtract(player.getUniqueId(), amount);
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Economy withdrawal failed: " + throwable.getMessage());
            return false;
        }
        return fallbackUsable() && store.debitFallback(player.getUniqueId(), player.getName(), amount);
    }

    public boolean deposit(Player player, double amount) {
        return player != null && deposit(player.getUniqueId(), player, amount);
    }

    public boolean deposit(UUID uuid, double amount) {
        return uuid != null && deposit(uuid, Bukkit.getOfflinePlayer(uuid), amount);
    }

    private boolean deposit(UUID uuid, OfflinePlayer player, double amount) {
        if (!finitePositive(amount)) {
            return false;
        }
        try {
            if (hasExternalProvider() && vault != null && player != null) {
                return vault.depositPlayer(player, amount).transactionSuccess();
            }
            if (hasExternalProvider() && essentials != null) {
                return essentials.add(uuid, amount);
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Economy deposit failed: " + throwable.getMessage());
            return false;
        }
        return fallbackUsable() && store.creditFallback(uuid, player == null ? null : player.getName(), amount);
    }

    public String format(double amount) {
        if (hasExternalProvider() && vault != null) {
            try {
                return vault.format(amount);
            } catch (Throwable ignored) {
                // use local format below
            }
        }
        if (hasExternalProvider() && essentials != null) {
            try {
                return essentials.format(amount);
            } catch (Throwable ignored) {
                // use local format below
            }
        }
        return dev.superseller.justgambling.util.Numbers.format(amount) + " " + settings.fallbackCurrency();
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        String name = event.getPlugin().getName();
        if (name.equalsIgnoreCase("Vault") || name.equalsIgnoreCase("Essentials") || name.equalsIgnoreCase("EssentialsX")) {
            hook();
        }
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (event.getProvider().getService() == Economy.class) {
            hook();
        }
    }

    private boolean hookVault() {
        try {
            Plugin vaultPlugin = Bukkit.getPluginManager().getPlugin("Vault");
            if (vaultPlugin == null || !vaultPlugin.isEnabled()) {
                return false;
            }
            RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (registration == null || registration.getProvider() == null || !registration.getProvider().isEnabled()) {
                return false;
            }
            vault = registration.getProvider();
            providerName = "Vault/" + vault.getName();
            return true;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not hook Vault: " + throwable.getMessage());
            vault = null;
            return false;
        }
    }

    private boolean hookEssentials() {
        Plugin essentialsPlugin = Bukkit.getPluginManager().getPlugin("Essentials");
        if (essentialsPlugin == null || !essentialsPlugin.isEnabled()) {
            return false;
        }
        try {
            essentials = EssentialsEconomy.bind();
            providerName = "EssentialsX native";
            return essentials != null;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not hook native EssentialsX economy: " + throwable.getMessage());
            essentials = null;
            return false;
        }
    }

    private boolean fallbackUsable() {
        return !settings.economyMode().equals("vault") && settings.fallbackEnabled();
    }

    private void logChange(String old) {
        if (!providerName.equals(old)) {
            plugin.getLogger().info("JustGambling hooked economy: " + providerName);
        }
    }

    private static double finite(double amount) {
        return Double.isFinite(amount) && amount >= 0.0 ? amount : 0.0;
    }

    private static boolean finitePositive(double amount) {
        return Double.isFinite(amount) && amount > 0.0;
    }

    /** Reflection keeps native EssentialsX support optional at compile time. */
    private static final class EssentialsEconomy {
        private final Method getMoneyExact;
        private final Method subtract;
        private final Method add;
        private final Method format;
        private final Method playerExists;

        private EssentialsEconomy(Method getMoneyExact, Method subtract, Method add, Method format, Method playerExists) {
            this.getMoneyExact = getMoneyExact;
            this.subtract = subtract;
            this.add = add;
            this.format = format;
            this.playerExists = playerExists;
        }

        private static EssentialsEconomy bind() throws ReflectiveOperationException {
            Class<?> type = Class.forName("com.earth2me.essentials.api.Economy");
            Method getMoneyExact = type.getMethod("getMoneyExact", UUID.class);
            Method subtract = type.getMethod("subtract", UUID.class, BigDecimal.class);
            Method add = type.getMethod("add", UUID.class, BigDecimal.class);
            Method format;
            try {
                format = type.getMethod("format", BigDecimal.class);
            } catch (NoSuchMethodException ignored) {
                format = type.getMethod("format", double.class);
            }
            Method playerExists = type.getMethod("playerExists", UUID.class);
            return new EssentialsEconomy(getMoneyExact, subtract, add, format, playerExists);
        }

        private double balance(UUID uuid) throws ReflectiveOperationException {
            if (!exists(uuid)) {
                return 0.0;
            }
            Object result = getMoneyExact.invoke(null, uuid);
            return result instanceof Number number ? number.doubleValue() : 0.0;
        }

        private boolean subtract(UUID uuid, double amount) {
            try {
                if (!exists(uuid)) {
                    return false;
                }
                subtract.invoke(null, uuid, BigDecimal.valueOf(amount));
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        private boolean add(UUID uuid, double amount) {
            try {
                add.invoke(null, uuid, BigDecimal.valueOf(amount));
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        private String format(double amount) throws ReflectiveOperationException {
            Object result;
            if (format.getParameterTypes()[0] == BigDecimal.class) {
                result = format.invoke(null, BigDecimal.valueOf(amount));
            } else {
                result = format.invoke(null, amount);
            }
            return result == null ? dev.superseller.justgambling.util.Numbers.format(amount) : String.valueOf(result);
        }

        private boolean exists(UUID uuid) throws ReflectiveOperationException {
            Object result = playerExists.invoke(null, uuid);
            return result instanceof Boolean value && value;
        }
    }
}
