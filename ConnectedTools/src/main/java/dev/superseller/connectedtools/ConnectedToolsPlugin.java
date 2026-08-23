package dev.superseller.connectedtools;

import dev.superseller.connectedtools.model.ConnectionStore;
import org.bukkit.plugin.java.JavaPlugin;

public class ConnectedToolsPlugin extends JavaPlugin {

    private static ConnectedToolsPlugin instance;
    private final ConnectionStore store = new ConnectionStore();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        getLogger().info("ConnectedTools v" + getDescription().getVersion() + " enabled for Paper / Purpur / Folia 26.2.");
        registerCommands();
        registerListeners();
    }

    @Override
    public void onDisable() {
        getLogger().info("ConnectedTools v" + getDescription().getVersion() + " disabled.");
        instance = null;
    }

    public static ConnectedToolsPlugin getInstance() {
        return instance;
    }

    public ConnectionStore getStore() {
        return store;
    }

    private void registerCommands() {
        getCommand("connectedtools").setExecutor(new dev.superseller.connectedtools.command.ConnectedToolsCommand());
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new dev.superseller.connectedtools.listener.PlayerInteractListener(), this);
        getServer().getPluginManager().registerEvents(new dev.superseller.connectedtools.listener.GUIListener(), this);
    }
}
