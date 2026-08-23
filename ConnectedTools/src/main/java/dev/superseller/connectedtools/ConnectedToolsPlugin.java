package dev.superseller.connectedtools;

import dev.superseller.connectedtools.api.ConnectionAPI;
import dev.superseller.connectedtools.command.ConnectedToolsCommand;
import dev.superseller.connectedtools.config.PluginSettings;
import dev.superseller.connectedtools.gui.MenuHolder;
import dev.superseller.connectedtools.listener.GUIListener;
import dev.superseller.connectedtools.listener.PlayerInteractListener;
import dev.superseller.connectedtools.model.ConnectionStore;
import dev.superseller.connectedtools.scheduler.PlatformScheduler;
import dev.superseller.connectedtools.service.ConnectionService;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConnectedToolsPlugin extends JavaPlugin {

    private static ConnectedToolsPlugin instance;
    private PluginSettings settings;
    private ConnectionStore store;
    private PlatformScheduler scheduler;
    private ConnectionService service;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("messages.yml", false);

        settings = new PluginSettings(this);
        settings.load();

        store = new ConnectionStore(this);
        scheduler = new PlatformScheduler(this);
        service = new ConnectionService(this, store, settings);

        registerCommands();
        registerListeners();

        getLogger().info("ConnectedTools v" + getDescription().getVersion()
                + " enabled for Paper / Purpur / Folia 26.2 (Java 25)");
        getLogger().info("Redstone mechanism: vanilla toggling (levers, buttons, doors, gates, wires, observers, comparators, repeaters)");
        getLogger().info("Commands: /connectedtools (aliases: /ct, /conn, /conntools) with tab completers");
    }

    @Override
    public void onDisable() {
        getLogger().info("ConnectedTools v" + getDescription().getVersion() + " disabled.");
        instance = null;
    }

    public static ConnectedToolsPlugin getInstance() {
        return instance;
    }

    public PluginSettings settings() {
        return settings;
    }

    public ConnectionStore getStore() {
        return store;
    }

    public ConnectionService getService() {
        return service;
    }

    public PlatformScheduler getScheduler() {
        return scheduler;
    }

    private void registerCommands() {
        org.bukkit.command.PluginCommand root = getCommand("connectedtools");
        if (root != null) {
            ConnectedToolsCommand executor = new ConnectedToolsCommand(settings, store, service);
            root.setExecutor(executor);
            root.setTabCompleter(executor);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerInteractListener(service), this);
        getServer().getPluginManager().registerEvents(new GUIListener(store), this);
    }
}
