package dev.superseller.playerheads;

import dev.superseller.playerheads.command.PlayerHeadsCommand;
import dev.superseller.playerheads.config.Messages;
import dev.superseller.playerheads.config.PlayerHeadsConfig;
import dev.superseller.playerheads.head.HeadService;
import dev.superseller.playerheads.scheduler.PlatformScheduler;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PlayerHeads — give server admins the player head of any Minecraft account.
 *
 * <p>Targets Minecraft 26.2 on Paper, Purpur and Folia. Skin lookups run
 * asynchronously; item hand-out always happens on the receiving player's
 * region thread, so the plugin is Folia-safe.</p>
 */
public final class PlayerHeadsPlugin extends JavaPlugin {

    private PlayerHeadsConfig settings;
    private Messages messages;
    private HeadService headService;

    @Override
    public void onEnable() {
        PlatformScheduler.init(this);

        settings = new PlayerHeadsConfig(this);
        settings.load();

        messages = new Messages(this);
        messages.load();

        headService = new HeadService(this, settings, messages);

        PluginCommand command = getCommand("playerheads");
        if (command != null) {
            PlayerHeadsCommand executor = new PlayerHeadsCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("Command 'playerheads' is missing from plugin.yml — the plugin will not be usable!");
        }

        getLogger().info("PlayerHeads enabled (Minecraft 26.2, Paper / Purpur / Folia).");
    }

    @Override
    public void onDisable() {
        getLogger().info("PlayerHeads disabled.");
    }

    /** Reloads config.yml and messages.yml from disk. */
    public void reloadAll() {
        settings.load();
        messages.load();
    }

    public PlayerHeadsConfig settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public HeadService headService() {
        return headService;
    }
}
