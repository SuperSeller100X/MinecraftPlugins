package dev.superseller.justgambling;

import dev.superseller.justgambling.command.JustGamblingAdminCommand;
import dev.superseller.justgambling.command.JustGamblingCommand;
import dev.superseller.justgambling.config.Messages;
import dev.superseller.justgambling.config.PluginSettings;
import dev.superseller.justgambling.economy.EconomyService;
import dev.superseller.justgambling.game.GameService;
import dev.superseller.justgambling.gui.GamblingGui;
import dev.superseller.justgambling.input.InputManager;
import dev.superseller.justgambling.listener.ChatListener;
import dev.superseller.justgambling.listener.GuiListener;
import dev.superseller.justgambling.listener.PlayerListener;
import dev.superseller.justgambling.scheduler.PlatformScheduler;
import dev.superseller.justgambling.storage.GamblingStore;
import dev.superseller.justgambling.util.Sounds;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** JustGambling plugin bootstrap. */
public final class JustGamblingPlugin extends JavaPlugin {
    private PluginSettings settings;
    private Messages messages;
    private GamblingStore store;
    private EconomyService economy;
    private GameService games;
    private InputManager input;
    private GamblingGui gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        PlatformScheduler.init(this);

        settings = new PluginSettings(this);
        settings.load();
        messages = new Messages(this);
        messages.load();
        store = new GamblingStore(this, settings);
        store.load();

        economy = new EconomyService(this, store, settings);
        economy.start();
        Sounds sounds = new Sounds(settings);
        games = new GameService(this, settings, messages, economy, store, sounds);
        input = new InputManager(games, economy, messages);
        gui = new GamblingGui(settings, messages, economy, store, games, input);
        games.setGui(gui);

        registerCommands();
        getServer().getPluginManager().registerEvents(new GuiListener(gui), this);
        getServer().getPluginManager().registerEvents(new ChatListener(input), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(games, input), this);

        long period = Math.max(5L, settings.autoSaveSeconds()) * 20L;
        PlatformScheduler.runGlobalRepeating(store::saveAsync, period, period);
        getLogger().info("JustGambling enabled for Minecraft 26.2. Economy: " + economy.providerName()
                + ". Folia scheduler: " + PlatformScheduler.isFoliaSchedulerAvailable());
    }

    @Override
    public void onDisable() {
        if (games != null) {
            games.shutdown();
        }
        if (store != null) {
            store.close();
        }
    }

    public void reloadEverything() {
        reloadConfig();
        settings.load();
        messages.load();
        if (economy != null) {
            economy.hook();
        }
    }

    private void registerCommands() {
        PluginCommand command = getCommand("justgambling");
        if (command != null) {
            JustGamblingCommand executor = new JustGamblingCommand(this, settings, messages, economy, store, games, gui);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        PluginCommand adminCommand = getCommand("justgamblingadmin");
        if (adminCommand != null) {
            JustGamblingAdminCommand executor = new JustGamblingAdminCommand(this, settings, messages, economy, store, games, gui);
            adminCommand.setExecutor(executor);
            adminCommand.setTabCompleter(executor);
        }
    }

    public PluginSettings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public GamblingStore store() {
        return store;
    }

    public EconomyService economy() {
        return economy;
    }

    public GameService games() {
        return games;
    }
}
