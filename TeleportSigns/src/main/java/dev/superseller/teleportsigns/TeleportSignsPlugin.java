package dev.superseller.teleportsigns;

import dev.superseller.teleportsigns.command.TeleportSignsCommand;
import dev.superseller.teleportsigns.config.Messages;
import dev.superseller.teleportsigns.config.PluginSettings;
import dev.superseller.teleportsigns.economy.EconomyHook;
import dev.superseller.teleportsigns.listener.InteractListener;
import dev.superseller.teleportsigns.listener.ProtectListener;
import dev.superseller.teleportsigns.scheduler.PlatformScheduler;
import dev.superseller.teleportsigns.service.TeleportService;
import dev.superseller.teleportsigns.storage.SignStore;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * TeleportSigns — bind a destination to a written sign and right-click to go.
 *
 * <p>Targets Minecraft 26.2 on Paper, Purpur and Folia. Teleports use
 * {@code teleportAsync}; scheduling goes through the entity/region schedulers
 * so the plugin is Folia-safe.</p>
 */
public final class TeleportSignsPlugin extends JavaPlugin {

    private PluginSettings settings;
    private Messages messages;
    private EconomyHook economy;
    private SignStore store;
    private TeleportService teleports;

    @Override
    public void onEnable() {
        PlatformScheduler.init(this);

        settings = new PluginSettings(this);
        settings.load();

        messages = new Messages(this);
        messages.load();

        economy = new EconomyHook(this);
        economy.init(settings.economyMode());

        store = new SignStore(this);
        store.load();

        teleports = new TeleportService(this);

        getServer().getPluginManager().registerEvents(new InteractListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectListener(this), this);

        PluginCommand command = getCommand("teleportsigns");
        if (command != null) {
            TeleportSignsCommand executor = new TeleportSignsCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("Command 'teleportsigns' is missing from plugin.yml — the plugin will not be usable!");
        }

        getLogger().info("TeleportSigns enabled (Minecraft 26.2, Paper / Purpur / Folia).");
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.save();
        }
        getLogger().info("TeleportSigns disabled.");
    }

    public void reloadAll() {
        settings.load();
        messages.load();
        economy.init(settings.economyMode());
        store.load();
    }

    public PluginSettings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public EconomyHook economy() {
        return economy;
    }

    public SignStore store() {
        return store;
    }

    public TeleportService teleports() {
        return teleports;
    }
}
