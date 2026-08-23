package dev.superseller.playerbank.economy;

import dev.superseller.playerbank.PlayerBankPlugin;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Wallet (normal economy) only. Bank balances never go through Vault or
 * EssentialsX, so other plugins cannot spend banked money.
 *
 * <p>Prefers a Vault provider (EssentialsX registers one). If Vault is missing,
 * talks to EssentialsX's native {@code com.earth2me.essentials.api.Economy}.
 */
public final class VaultHook implements Listener {

    private final PlayerBankPlugin plugin;
    private Economy vault;
    private EssentialsEconomy essentials;
    private boolean enabled;
    private String providerName = "none";
    private boolean listening;

    public VaultHook(PlayerBankPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!listening) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            listening = true;
        }
        hook();
        // EssentialsX / Vault often finish registering one tick after onEnable.
        plugin.getServer().getScheduler().runTaskLater(plugin, this::hook, 1L);
    }

    public void hook() {
        String previous = providerName;
        vault = null;
        essentials = null;
        enabled = false;
        providerName = "none";

        if (hookVault()) {
            logIfChanged(previous);
            return;
        }
        if (hookEssentials()) {
            logIfChanged(previous);
            return;
        }
        if (!"none".equals(previous)) {
            plugin.getLogger().warning("Lost wallet economy provider.");
        }
    }

    private void logIfChanged(String previous) {
        if (!providerName.equals(previous)) {
            plugin.getLogger().info("Hooked wallet economy: " + providerName);
        }
    }

    private boolean hookVault() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
                return false;
            }
            RegisteredServiceProvider<Economy> rsp =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp == null || rsp.getProvider() == null) {
                return false;
            }
            vault = rsp.getProvider();
            if (!vault.isEnabled()) {
                vault = null;
                return false;
            }
            enabled = true;
            providerName = "Vault/" + vault.getName();
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not hook Vault: " + t.getMessage());
            vault = null;
            return false;
        }
    }

    private boolean hookEssentials() {
        Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
        if (ess == null || !ess.isEnabled()) {
            return false;
        }
        try {
            essentials = EssentialsEconomy.bind();
            if (essentials == null) {
                return false;
            }
            enabled = true;
            providerName = "EssentialsX";
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not hook EssentialsX economy: " + t.getMessage());
            essentials = null;
            return false;
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        String name = event.getPlugin().getName();
        if ("Vault".equals(name) || "Essentials".equals(name)) {
            hook();
        }
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (event.getProvider().getService() == Economy.class) {
            hook();
        }
    }

    public boolean isEnabled() {
        return enabled && (vault != null || essentials != null);
    }

    public String providerName() {
        return providerName;
    }

    public double wallet(OfflinePlayer player) {
        if (!isEnabled() || player == null) {
            return 0;
        }
        try {
            if (vault != null) {
                return vault.getBalance(player);
            }
            return essentials.balance(player.getUniqueId());
        } catch (Throwable t) {
            plugin.getLogger().warning("Wallet lookup failed: " + t.getMessage());
            return 0;
        }
    }

    public boolean takeWallet(OfflinePlayer player, double amount) {
        if (!isEnabled() || player == null || amount <= 0) {
            return false;
        }
        try {
            if (vault != null) {
                return vault.withdrawPlayer(player, amount).transactionSuccess();
            }
            return essentials.subtract(player.getUniqueId(), amount);
        } catch (Throwable t) {
            plugin.getLogger().warning("Wallet withdraw failed: " + t.getMessage());
            return false;
        }
    }

    public boolean giveWallet(OfflinePlayer player, double amount) {
        if (!isEnabled() || player == null || amount <= 0) {
            return false;
        }
        try {
            if (vault != null) {
                return vault.depositPlayer(player, amount).transactionSuccess();
            }
            return essentials.add(player.getUniqueId(), amount);
        } catch (Throwable t) {
            plugin.getLogger().warning("Wallet deposit failed: " + t.getMessage());
            return false;
        }
    }

    public String format(double amount) {
        if (!isEnabled()) {
            return String.format("%.2f", amount);
        }
        try {
            if (vault != null) {
                return vault.format(amount);
            }
            return essentials.format(amount);
        } catch (Throwable t) {
            return String.format("%.2f", amount);
        }
    }

    /**
     * Calls EssentialsX economy through reflection so PlayerBank does not have
     * to compile against a specific EssentialsX jar.
     */
    private static final class EssentialsEconomy {
        private final Method getMoneyExact;
        private final Method subtractMethod;
        private final Method addMethod;
        private final Method formatMethod;
        private final Method playerExists;

        private EssentialsEconomy(Method getMoneyExact, Method subtractMethod, Method addMethod,
                                  Method formatMethod, Method playerExists) {
            this.getMoneyExact = getMoneyExact;
            this.subtractMethod = subtractMethod;
            this.addMethod = addMethod;
            this.formatMethod = formatMethod;
            this.playerExists = playerExists;
        }

        static EssentialsEconomy bind() throws Exception {
            Class<?> type = Class.forName("com.earth2me.essentials.api.Economy");
            Method getMoneyExact = type.getMethod("getMoneyExact", UUID.class);
            Method subtractMethod = type.getMethod("subtract", UUID.class, BigDecimal.class);
            Method addMethod = type.getMethod("add", UUID.class, BigDecimal.class);
            Method formatMethod;
            try {
                formatMethod = type.getMethod("format", BigDecimal.class);
            } catch (NoSuchMethodException e) {
                formatMethod = type.getMethod("format", double.class);
            }
            Method playerExists = type.getMethod("playerExists", UUID.class);
            return new EssentialsEconomy(getMoneyExact, subtractMethod, addMethod, formatMethod, playerExists);
        }

        double balance(UUID uuid) throws Exception {
            if (!exists(uuid)) {
                return 0;
            }
            Object value = getMoneyExact.invoke(null, uuid);
            if (value instanceof BigDecimal bd) {
                return bd.doubleValue();
            }
            if (value instanceof Number n) {
                return n.doubleValue();
            }
            return 0;
        }

        boolean subtract(UUID uuid, double amount) {
            try {
                if (!exists(uuid)) {
                    return false;
                }
                subtractMethod.invoke(null, uuid, BigDecimal.valueOf(amount));
                return true;
            } catch (Throwable t) {
                return false;
            }
        }

        boolean add(UUID uuid, double amount) {
            try {
                addMethod.invoke(null, uuid, BigDecimal.valueOf(amount));
                return true;
            } catch (Throwable t) {
                return false;
            }
        }

        String format(double amount) throws Exception {
            Object value;
            if (formatMethod.getParameterTypes()[0] == BigDecimal.class) {
                value = formatMethod.invoke(null, BigDecimal.valueOf(amount));
            } else {
                value = formatMethod.invoke(null, amount);
            }
            return value == null ? String.format("%.2f", amount) : String.valueOf(value);
        }

        private boolean exists(UUID uuid) throws Exception {
            Object value = playerExists.invoke(null, uuid);
            return value instanceof Boolean b && b;
        }
    }
}
