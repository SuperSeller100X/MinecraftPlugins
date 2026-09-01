package dev.superseller.shardtools;

import java.util.logging.Level;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import dev.superseller.shardtools.command.ShardToolsCommand;
import dev.superseller.shardtools.config.Messages;
import dev.superseller.shardtools.config.RuntimeStore;
import dev.superseller.shardtools.config.Settings;
import dev.superseller.shardtools.economy.AwardService;
import dev.superseller.shardtools.economy.VaultBridge;
import dev.superseller.shardtools.economy.ShardAccounts;
import dev.superseller.shardtools.expiry.ExpirySweep;
import dev.superseller.shardtools.gui.ShopGui;
import dev.superseller.shardtools.item.Effects;
import dev.superseller.shardtools.item.EnchantResolver;
import dev.superseller.shardtools.item.ShardCatalog;
import dev.superseller.shardtools.item.ShardItems;
import dev.superseller.shardtools.item.SoundResolver;
import dev.superseller.shardtools.listener.AnvilListener;
import dev.superseller.shardtools.listener.EquipListener;
import dev.superseller.shardtools.listener.BreakListener;
import dev.superseller.shardtools.listener.ExpiryListener;
import dev.superseller.shardtools.listener.PotionListener;
import dev.superseller.shardtools.listener.ShopListener;
import dev.superseller.shardtools.scheduler.PlatformScheduler;
import dev.superseller.shardtools.shop.PriceBook;

/**
 * ShardTools - DonutSMP-style shard economy for Minecraft 26.2.
 *
 * Runs on Paper, Purpur and Folia (regionized scheduling), on any OS with
 * a Java 25 VM. All gameplay values live in config.yml / messages.yml.
 */
public final class ShardToolsPlugin extends JavaPlugin {

    private Settings settings;
    private Messages messages;
    private RuntimeStore runtimeStore;
    private ShardCatalog catalog;
    private PriceBook priceBook;
    private ShardAccounts accounts;
    private ShardItems items;
    private ExpirySweep sweep;
    private ShopGui gui;
    private Effects effects;
    private EnchantResolver enchantResolver;
    private SoundResolver soundResolver;
    private AwardService awardService;
    private VaultBridge vault;

    @Override
    public void onEnable() {
        PlatformScheduler.init(this);
        saveDefaultConfig();
        settings = new Settings(this);
        settings.load();
        messages = new Messages(this);
        runtimeStore = new RuntimeStore(this);
        catalog = ShardCatalog.load(getConfig(), getLogger());
        priceBook = new PriceBook(catalog.defaultPrices(), runtimeStore.priceOverrides());
        accounts = new ShardAccounts(this);
        accounts.load();
        vault = new VaultBridge(this);
        effects = new Effects(this);
        enchantResolver = new EnchantResolver(getLogger());
        soundResolver = new SoundResolver(getLogger());
        items = new ShardItems(this);
        sweep = new ExpirySweep(this);
        gui = new ShopGui(this);
        awardService = new AwardService(this);

        PluginManager manager = getServer().getPluginManager();
        manager.registerEvents(new BreakListener(this), this);
        manager.registerEvents(new ShopListener(this), this);
        manager.registerEvents(new PotionListener(this), this);
        manager.registerEvents(new ExpiryListener(this), this);
        manager.registerEvents(new AnvilListener(this), this);
        manager.registerEvents(new EquipListener(this), this);

        ShardToolsCommand command = new ShardToolsCommand(this);
        PluginCommand shardtools = getCommand("shardtools");
        if (shardtools != null) {
            shardtools.setExecutor(command);
            shardtools.setTabCompleter(command);
        } else {
            getLogger().severe("Command 'shardtools' missing from plugin.yml");
        }

        restartTasks();
        getLogger().info("ShardTools enabled: " + catalog.ordered().size() + " shop items, awarding "
                + effectiveAwardAmount() + " shards every " + effectiveAwardIntervalMinutes() + " min");
    }

    @Override
    public void onDisable() {
        try {
            PlatformScheduler.cancelAll();
            if (accounts != null) {
                accounts.saveNow();
            }
            if (runtimeStore != null) {
                runtimeStore.saveNow();
            }
        } catch (Throwable error) {
            getLogger().log(Level.WARNING, "Error while disabling", error);
        }
        getLogger().info("ShardTools disabled");
    }

    /** (Re-)starts the award and expiry sweep tasks. */
    public void restartTasks() {
        PlatformScheduler.cancelAll();
        if (effectiveAwardEnabled()) {
            long intervalTicks = effectiveAwardIntervalMinutes() * 60L * 20L;
            PlatformScheduler.runRepeating(awardService::tick, intervalTicks, intervalTicks);
        }
        long sweepTicks = settings.sweepSeconds() * 20L;
        PlatformScheduler.runRepeating(sweep::tick, sweepTicks, sweepTicks);
    }

    /** Reloads config.yml, messages.yml, runtime overrides and the catalog. */
    public void reloadAll() {
        reloadConfig();
        settings.load();
        messages.reload();
        runtimeStore.reload();
        catalog = ShardCatalog.load(getConfig(), getLogger());
        priceBook.reload(catalog.defaultPrices(), runtimeStore.priceOverrides());
        vault.reconnect();
        restartTasks();
    }

    public long effectiveAwardIntervalMinutes() {
        Long override = runtimeStore.awardIntervalOverride();
        return override != null ? Math.max(1L, override) : settings.awardIntervalMinutes();
    }

    public long effectiveAwardAmount() {
        Long override = runtimeStore.awardAmountOverride();
        return override != null ? Math.max(0L, override) : settings.awardAmount();
    }

    public boolean effectiveAwardEnabled() {
        Boolean override = runtimeStore.awardEnabledOverride();
        return override != null ? override : settings.awardEnabled();
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public RuntimeStore runtimeStore() {
        return runtimeStore;
    }

    public ShardCatalog catalog() {
        return catalog;
    }

    public PriceBook priceBook() {
        return priceBook;
    }

    public ShardAccounts accounts() {
        return accounts;
    }

    public ShardItems items() {
        return items;
    }

    public ExpirySweep sweep() {
        return sweep;
    }

    public ShopGui gui() {
        return gui;
    }

    public EnchantResolver enchantResolver() {
        return enchantResolver;
    }

    public Effects effects() {
        return effects;
    }

    public SoundResolver soundResolver() {
        return soundResolver;
    }

    public AwardService awardService() {
        return awardService;
    }

    public VaultBridge vault() {
        return vault;
    }
}
