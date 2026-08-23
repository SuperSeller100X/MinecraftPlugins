package dev.superseller.randomstructurechallenge;

import dev.superseller.randomstructurechallenge.challenge.ChallengeManager;
import dev.superseller.randomstructurechallenge.challenge.StructureCatalog;
import dev.superseller.randomstructurechallenge.challenge.StructurePlacer;
import dev.superseller.randomstructurechallenge.challenge.TimerDisplay;
import dev.superseller.randomstructurechallenge.command.ChallengeCommand;
import dev.superseller.randomstructurechallenge.config.Messages;
import dev.superseller.randomstructurechallenge.config.PluginSettings;
import dev.superseller.randomstructurechallenge.listener.ChatInputListener;
import dev.superseller.randomstructurechallenge.listener.PlayerConnectionListener;
import dev.superseller.randomstructurechallenge.scheduler.PlatformScheduler;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RandomStructureChallenge — drop a random vanilla structure on every online
 * player on a ticking interval. Targets Minecraft 26.2 (Paper / Purpur / Folia).
 */
public final class RandomStructureChallengePlugin extends JavaPlugin {

    private PluginSettings settings;
    private Messages messages;
    private StructureCatalog catalog;
    private ChallengeManager manager;

    @Override
    public void onEnable() {
        PlatformScheduler.init(this);

        settings = new PluginSettings(this);
        settings.load();

        messages = new Messages(this);
        messages.load();

        catalog = new StructureCatalog(settings, getLogger());
        catalog.refresh();

        StructurePlacer placer = new StructurePlacer(this, settings, catalog);
        TimerDisplay display = new TimerDisplay(this, settings, messages);
        manager = new ChallengeManager(this, settings, messages, catalog, placer, display);

        PluginCommand command = getCommand("challenge");
        if (command != null) {
            ChallengeCommand executor = new ChallengeCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("Command 'challenge' is missing from plugin.yml — the plugin will not be usable!");
        }

        getServer().getPluginManager().registerEvents(new ChatInputListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

        PlatformScheduler.runTimer(() -> manager.tick(), 20L);

        getLogger().info("RandomStructureChallenge enabled (Minecraft 26.2, Paper / Purpur / Folia, Java 25).");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
        getLogger().info("RandomStructureChallenge disabled.");
    }

    public void reloadAll() {
        settings.load();
        messages.load();
        catalog.refresh();
        getLogger().info("Configuration reloaded.");
    }

    public PluginSettings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public ChallengeManager manager() {
        return manager;
    }

    public StructureCatalog catalog() {
        return catalog;
    }
}
