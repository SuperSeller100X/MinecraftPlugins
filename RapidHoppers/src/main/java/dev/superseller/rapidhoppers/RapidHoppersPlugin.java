package dev.superseller.rapidhoppers;

import dev.superseller.rapidhoppers.command.RapidHoppersAdminCommand;
import dev.superseller.rapidhoppers.command.RapidHoppersCommand;
import dev.superseller.rapidhoppers.config.ConfigService;
import dev.superseller.rapidhoppers.config.Messages;
import dev.superseller.rapidhoppers.config.Settings;
import dev.superseller.rapidhoppers.engine.HopperEngine;
import dev.superseller.rapidhoppers.engine.Stats;
import dev.superseller.rapidhoppers.engine.ThrottleMonitor;
import dev.superseller.rapidhoppers.gui.ControlPanel;
import dev.superseller.rapidhoppers.listener.TransferListener;
import dev.superseller.rapidhoppers.scheduler.PlatformScheduler;
import dev.superseller.rapidhoppers.util.Sounds;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RapidHoppers — configurable high-speed item transport for Minecraft 26.2
 * (Paper / Purpur / Folia), built for Java 25.
 */
public final class RapidHoppersPlugin extends JavaPlugin {

    public static final String VERSION = "1.0.0";
    public static final String API_VERSION = "26.2";

    private ConfigService configService;
    private Messages messages;
    private Sounds sounds;
    private Stats stats;
    private ThrottleMonitor throttle;
    private HopperEngine engine;
    private ControlPanel panel;
    private PlatformScheduler.Handle guiRefreshTask;

    private int containerCursor;

    @Override
    public void onEnable() {
        PlatformScheduler.init(this);

        configService = new ConfigService(this);
        configService.load();

        messages = new Messages(this);
        messages.load();

        sounds = new Sounds(this, settings());
        stats = new Stats();
        throttle = new ThrottleMonitor(this, settings());
        engine = new HopperEngine(this, settings(), throttle, stats);
        panel = new ControlPanel(this);

        getServer().getPluginManager().registerEvents(panel, this);
        getServer().getPluginManager().registerEvents(
                new TransferListener(settings(), throttle, stats), this);

        registerCommands();

        throttle.start();
        engine.start();
        startGuiRefresh();

        getLogger().info("RapidHoppers v" + VERSION + " enabled on " + PlatformScheduler.platformName()
                + " — interval " + settings().getIntervalTicks() + " ticks ("
                + settings().speedFactor() + "x vanilla), "
                + settings().getItemsPerTransfer() + " items per transfer.");
    }

    @Override
    public void onDisable() {
        if (engine != null) {
            engine.stop();
        }
        if (throttle != null) {
            throttle.stop();
        }
        if (guiRefreshTask != null) {
            guiRefreshTask.cancel();
            guiRefreshTask = null;
        }
        PlatformScheduler.cancelAll();
        getLogger().info("RapidHoppers disabled.");
    }

    private void registerCommands() {
        PluginCommand base = getCommand("rapidhoppers");
        if (base != null) {
            RapidHoppersCommand executor = new RapidHoppersCommand(this);
            base.setExecutor(executor);
            base.setTabCompleter(executor);
        }
        PluginCommand admin = getCommand("rapidhoppersadmin");
        if (admin != null) {
            RapidHoppersAdminCommand executor = new RapidHoppersAdminCommand(this);
            admin.setExecutor(executor);
            admin.setTabCompleter(executor);
        }
    }

    private void startGuiRefresh() {
        if (guiRefreshTask != null) {
            guiRefreshTask.cancel();
            guiRefreshTask = null;
        }
        int ticks = settings().getGuiRefreshTicks();
        if (ticks > 0) {
            guiRefreshTask = PlatformScheduler.runTimer(panel::refreshOpenPanels, ticks, ticks);
        }
    }

    /** Reloads config.yml and messages.yml and restarts the timed components. */
    public void reloadAll() {
        configService.load();
        messages.load();
        throttle.start();
        engine.start();
        startGuiRefresh();
    }

    /** Round-robin cursor used by the GUI container-type button. */
    public int nextContainerCursor(int size) {
        int n = Math.max(1, size);
        int current = containerCursor % n;
        containerCursor = (current + 1) % n;
        return current;
    }

    public ConfigService configService() {
        return configService;
    }

    public Settings settings() {
        return configService.settings();
    }

    public Messages messages() {
        return messages;
    }

    public Sounds sounds() {
        return sounds;
    }

    public Stats stats() {
        return stats;
    }

    public ThrottleMonitor throttle() {
        return throttle;
    }

    public HopperEngine engine() {
        return engine;
    }

    public ControlPanel panel() {
        return panel;
    }
}
