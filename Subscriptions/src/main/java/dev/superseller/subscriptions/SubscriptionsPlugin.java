package dev.superseller.subscriptions;

import java.io.File;

import dev.superseller.subscriptions.api.SubscriptionsAPI;
import dev.superseller.subscriptions.command.InboxCommand;
import dev.superseller.subscriptions.command.SubAdminCommand;
import dev.superseller.subscriptions.command.SubCommand;
import dev.superseller.subscriptions.config.PluginSettings;
import dev.superseller.subscriptions.economy.EconomyService;
import dev.superseller.subscriptions.gui.GuiManager;
import dev.superseller.subscriptions.hook.CsvLogger;
import dev.superseller.subscriptions.hook.DiscordHook;
import dev.superseller.subscriptions.hook.LuckPermsHook;
import dev.superseller.subscriptions.hook.PlaceholderHook;
import dev.superseller.subscriptions.input.ChatInput;
import dev.superseller.subscriptions.listener.ChatListener;
import dev.superseller.subscriptions.listener.GuiListener;
import dev.superseller.subscriptions.listener.JoinListener;
import dev.superseller.subscriptions.scheduler.PlatformScheduler;
import dev.superseller.subscriptions.service.BillingService;
import dev.superseller.subscriptions.service.InboxService;
import dev.superseller.subscriptions.storage.Database;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Subscriptions — recurring marketplace for Paper / Purpur / Folia 26.2.
 */
public final class SubscriptionsPlugin extends JavaPlugin {

    private PluginSettings settings;
    private EconomyService economy;
    private Database database;
    private PlatformScheduler scheduler;
    private BillingService billing;
    private SubscriptionsAPI api;

    @Override
    public void onEnable() {
        settings = new PluginSettings(this);
        settings.load();

        scheduler = new PlatformScheduler(this);
        economy = new EconomyService(this);
        economy.init(settings.economyMode());

        database = new Database(new File(getDataFolder(), "subscriptions.db"), getLogger());
        database.open();
        database.pruneCharges(settings.csvRetentionDays());

        LuckPermsHook luckPerms = new LuckPermsHook(getLogger());
        luckPerms.init();

        DiscordHook discord = new DiscordHook(settings, scheduler, getLogger());
        CsvLogger csv = new CsvLogger(new File(getDataFolder(), "logs"), settings, getLogger());
        InboxService inbox = new InboxService(database, settings);
        billing = new BillingService(this, database, economy, inbox, settings, luckPerms, discord, csv, scheduler);

        ChatInput chat = new ChatInput(settings, scheduler);
        GuiManager gui = new GuiManager(this, settings, database, economy, billing, inbox, chat);
        chat.handler(gui::handlePrompt);

        registerCommands(gui, discord);
        getServer().getPluginManager().registerEvents(new GuiListener(gui), this);
        getServer().getPluginManager().registerEvents(new ChatListener(chat), this);
        getServer().getPluginManager().registerEvents(new JoinListener(database, settings, gui), this);

        new PlaceholderHook(this, database).register();
        api = new SubscriptionsAPI(this);

        scheduler.runTimer(() -> {
            billing.tryResumePaused();
            billing.tick();
            chat.tick();
        }, settings.billingPeriodTicks());

        getLogger().info("Subscriptions v" + getPluginMeta().getVersion()
                + " enabled on " + (scheduler.folia() ? "Paper/Folia schedulers" : "Bukkit scheduler")
                + " · economy=" + (economy.isEnabled() ? economy.providerName() : "disabled")
                + " · plans=" + database.planCount());
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
        }
        getLogger().info("Subscriptions disabled.");
    }

    public void reloadAll() {
        settings.load();
        economy.init(settings.economyMode());
    }

    public PluginSettings settings() {
        return settings;
    }

    public EconomyService economy() {
        return economy;
    }

    public Database database() {
        return database;
    }

    public BillingService billing() {
        return billing;
    }

    public SubscriptionsAPI api() {
        return api;
    }

    public PlatformScheduler scheduler() {
        return scheduler;
    }

    private void registerCommands(GuiManager gui, DiscordHook discord) {
        PluginCommand root = getCommand("subscriptions");
        if (root != null) {
            SubCommand executor = new SubCommand(settings, database, billing, gui);
            root.setExecutor(executor);
            root.setTabCompleter(executor);
        }
        PluginCommand inbox = getCommand("subinbox");
        if (inbox != null) {
            inbox.setExecutor(new InboxCommand(settings, gui));
        }
        PluginCommand admin = getCommand("subadmin");
        if (admin != null) {
            SubAdminCommand executor = new SubAdminCommand(this, settings, database, billing, discord, gui);
            admin.setExecutor(executor);
            admin.setTabCompleter(executor);
        }
    }
}
