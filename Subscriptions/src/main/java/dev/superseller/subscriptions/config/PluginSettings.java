package dev.superseller.subscriptions.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PluginSettings {

    private final JavaPlugin plugin;
    private final Logger logger;
    private FileConfiguration config;
    private YamlConfiguration messages;

    public PluginSettings(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        plugin.saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
    }

    public FileConfiguration raw() {
        return config;
    }

    public String msg(String key) {
        String value = messages.getString(key);
        if (value == null) {
            return "&cMissing message: " + key;
        }
        return value;
    }

    public String prefix() {
        return msg("prefix");
    }

    public List<String> msgList(String key) {
        List<String> list = messages.getStringList(key);
        return list == null ? List.of() : list;
    }

    public long minIntervalMs() {
        return Math.max(1_000L, config.getLong("limits.min-interval-seconds", 60L) * 1000L);
    }

    public long maxIntervalMs() {
        return Math.max(minIntervalMs(), config.getLong("limits.max-interval-seconds", 31_536_000L) * 1000L);
    }

    public double minPrice() {
        return Math.max(0d, config.getDouble("limits.min-price", 0d));
    }

    public double maxPrice() {
        return Math.max(minPrice(), config.getDouble("limits.max-price", 1_000_000_000_000d));
    }

    public int maxPlansPerPlayer() {
        return Math.max(1, config.getInt("limits.max-plans-per-player", 10));
    }

    public int maxActiveSubs() {
        return Math.max(1, config.getInt("limits.max-active-subscriptions", 20));
    }

    public int maxRewardsPerPlan() {
        return Math.max(1, config.getInt("limits.max-rewards-per-plan", 12));
    }

    public int maxInbox() {
        return Math.max(1, config.getInt("limits.max-inbox-items", 216));
    }

    public int nameMax() {
        return Math.max(3, config.getInt("limits.max-name-length", 32));
    }

    public int descriptionMax() {
        return Math.max(8, config.getInt("limits.max-description-length", 160));
    }

    public int decimalPlaces() {
        return Math.max(0, config.getInt("economy.decimal-places", 2));
    }

    public double taxPercent() {
        return Math.max(0d, Math.min(100d, config.getDouble("economy.tax-percent", 0d)));
    }

    public String economyMode() {
        return config.getString("economy.enabled", "auto");
    }

    public boolean acceptAllKeyword() {
        return config.getBoolean("economy.accept-all-keyword", true);
    }

    public long billingPeriodTicks() {
        long seconds = Math.max(1L, config.getLong("billing.tick-seconds", 1L));
        return seconds * 20L;
    }

    public boolean sounds() {
        return config.getBoolean("ui.sounds", true);
    }

    public String webhookUrl() {
        return config.getString("discord.webhook-url", "");
    }

    public boolean webhookCharges() {
        return config.getBoolean("discord.notify-charges", true);
    }

    public boolean webhookCancels() {
        return config.getBoolean("discord.notify-cancels", true);
    }

    public boolean webhookStock() {
        return config.getBoolean("discord.notify-stock", true);
    }

    public boolean csvLogs() {
        return config.getBoolean("logs.csv", true);
    }

    public int csvRetentionDays() {
        return config.getInt("logs.retention-days", 30);
    }

    public List<Material> blacklist() {
        List<Material> out = new ArrayList<>();
        for (String name : config.getStringList("items.blacklist")) {
            try {
                out.add(Material.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                logger.warning("Unknown blacklist material: " + name);
            }
        }
        return out;
    }

    public boolean notifyJoin() {
        return config.getBoolean("ui.notify-on-join", true);
    }

    public String menuTitle(String key, String fallback) {
        return config.getString("ui.titles." + key, fallback);
    }
}
