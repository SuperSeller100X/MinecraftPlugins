package dev.superseller.homesgui;

import java.io.File;
import java.util.Objects;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import dev.superseller.homesgui.command.AdminCommand;
import dev.superseller.homesgui.command.HomeCommand;
import dev.superseller.homesgui.config.HomesSettings;
import dev.superseller.homesgui.config.Messages;
import dev.superseller.homesgui.economy.EconomyHook;
import dev.superseller.homesgui.gui.HomeMenu;
import dev.superseller.homesgui.gui.HomeMenuListener;
import dev.superseller.homesgui.listener.ConnectionListener;
import dev.superseller.homesgui.scheduler.PlatformScheduler;
import dev.superseller.homesgui.service.HomeService;
import dev.superseller.homesgui.storage.HomesStorage;

/** HomesGUI plugin entry point for Minecraft 26.2 on Paper, Purpur, and Folia. */
public final class HomesGUIPlugin extends JavaPlugin {

    private volatile HomesSettings settings;
    private volatile Messages messages;
    private HomesStorage storage;
    private EconomyHook economy;
    private HomeService homeService;
    private HomeMenu menu;
    private ScheduledTask autosaveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        saveResource("homes.yml", false);
        reloadSettingsAndMessages();

        storage = new HomesStorage(this);
        storage.load(settings.homes().freeSlots());
        economy = new EconomyHook(this);
        economy.reload(settings.economy().enabled());
        homeService = new HomeService(this, storage, economy);
        menu = new HomeMenu(this, homeService);

        registerCommands();
        getServer().getPluginManager().registerEvents(new HomeMenuListener(this, homeService, menu), this);
        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);
        startAutosave();

        getLogger().info("HomesGUI enabled for Minecraft 26.2 with " + settings.homes().freeSlots()
                + " free home slot(s), " + (economy.available() ? "economy provider " + economy.providerName()
                : "no economy provider") + ".");
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        if (storage != null) {
            storage.flush();
        }
    }

    private void registerCommands() {
        HomeCommand homeCommand = new HomeCommand(this, homeService, menu);
        bind("home", homeCommand);
        bind("homes", homeCommand);
        bind("sethome", homeCommand);
        bind("delhome", homeCommand);
        bind("buyhome", homeCommand);
        bind("homesadmin", new AdminCommand(this, homeService, storage));
    }

    private void bind(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            throw new IllegalStateException("Command '" + name + "' is missing from plugin.yml");
        }
        command.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }

    private void reloadSettingsAndMessages() {
        reloadConfig();
        settings = HomesSettings.load(getConfig(), getLogger());
        messages = Messages.load(new File(getDataFolder(), "messages.yml"), getLogger());
        if (economy != null) {
            economy.reload(settings.economy().enabled());
        }
    }

    private void startAutosave() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        int seconds = settings.storage().autosaveSeconds();
        if (seconds > 0) {
            autosaveTask = PlatformScheduler.repeatAsync(this, storage::saveIfDirty, seconds);
        }
    }

    public void reloadPluginSettings() {
        reloadSettingsAndMessages();
        startAutosave();
    }

    public HomesSettings settings() {
        return Objects.requireNonNull(settings, "plugin settings not loaded");
    }

    public Messages messages() {
        return Objects.requireNonNull(messages, "plugin messages not loaded");
    }

    public HomesStorage storage() {
        return storage;
    }

    public EconomyHook economy() {
        return economy;
    }

    public HomeService homeService() {
        return homeService;
    }

    public HomeMenu menu() {
        return menu;
    }
}
