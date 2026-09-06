package dev.superseller.hourglass;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.superseller.hourglass.api.HourGlassApi;
import dev.superseller.hourglass.command.AdminCommand;
import dev.superseller.hourglass.command.PlayTimeCommand;
import dev.superseller.hourglass.config.GuiConfig;
import dev.superseller.hourglass.config.HourGlassConfig;
import dev.superseller.hourglass.config.Messages;
import dev.superseller.hourglass.config.YamlIO;
import dev.superseller.hourglass.gui.GuiService;
import dev.superseller.hourglass.integration.PlaceholderApiHook;
import dev.superseller.hourglass.listener.GuiListener;
import dev.superseller.hourglass.listener.PlaytimeListener;
import dev.superseller.hourglass.scheduler.PlatformScheduler;
import dev.superseller.hourglass.service.DisplayService;
import dev.superseller.hourglass.service.LeaderboardService;
import dev.superseller.hourglass.service.MilestoneService;
import dev.superseller.hourglass.service.PlaytimeService;
import dev.superseller.hourglass.service.TrackingService;
import dev.superseller.hourglass.sound.SoundService;
import dev.superseller.hourglass.storage.YamlPlayerStorage;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * HourGlass — real playtime tracking for Paper, Purpur and Folia (Minecraft
 * 26.2, Java 25).
 *
 * <p>Wiring is deliberately explicit: one {@link PlatformScheduler} timer per
 * second drives tracking, leaderboard refresh, milestone checks, the live
 * display and autosave (each of those throttles itself), so a reload can change
 * any interval without cancelling and re-adding Bukkit tasks.
 *
 * <p>Startup order matters: the platform scheduler is detected first, the
 * configs are read (so every service sees real values), the player files load in
 * the background, and commands/listeners are registered straight away — a player
 * who joins while the load is still running is measured from the second they
 * connect, they just appear in the leaderboard a moment later.
 */
public final class HourGlassPlugin extends JavaPlugin {

    private HourGlassConfig config;
    private Messages messages;
    private GuiConfig guiConfig;
    private SoundService sounds;
    private YamlPlayerStorage storage;
    private PlaytimeService playtime;
    private TrackingService tracking;
    private LeaderboardService leaderboards;
    private MilestoneService milestones;
    private DisplayService display;
    private GuiService gui;

    private final AtomicBoolean ticking = new AtomicBoolean(false);
    private PlatformScheduler.TaskHandle timer;
    private volatile long nextAutosaveMillis;
    private volatile boolean placeholdersRegistered;
    private volatile int loadedPlayers;

    @Override
    public void onEnable() {
        PlatformScheduler.init(this);

        config = new HourGlassConfig(this);
        messages = new Messages(this);
        guiConfig = new GuiConfig(this);
        sounds = new SoundService(this);

        storage = new YamlPlayerStorage(this, "players");
        playtime = new PlaytimeService(this, storage);
        tracking = new TrackingService(this, playtime);
        leaderboards = new LeaderboardService(this, playtime);
        milestones = new MilestoneService(this, playtime);
        display = new DisplayService(this, playtime, milestones);
        gui = new GuiService(this);
        HourGlassApi.install(this);

        reloadFiles();

        getServer().getPluginManager().registerEvents(new PlaytimeListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        bind("playtime", new PlayTimeCommand(this));
        bind("playtimeadmin", new AdminCommand(this));

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null && config.placeholdersEnabled()) {
            int count = PlaceholderApiHook.register(this);
            placeholdersRegistered = count > 0;
            if (count > 0) {
                getLogger().info("Registered " + count + " PlaceholderAPI expansion(s): "
                        + String.join(", ", config.placeholderAliases()) + "_*");
            }
        }

        timer = PlatformScheduler.runTimer(this::tick, 20L, 20L);

        // Load player files off the main thread, then finish setup on the main thread.
        storage.loadAll(config.maxPlayers(), playtime::adopt)
                .whenComplete((result, error) -> PlatformScheduler.runGlobal(() -> finishLoad(result, error)));

        getLogger().info("HourGlass " + getDescription().getVersion() + " ready - tracking realtime"
                + (config.idleDetection() ? " and active playtime" : " playtime")
                + " for " + getServer().getOnlinePlayers().size() + " online player(s).");
    }

    private void finishLoad(YamlPlayerStorage.LoadResult result, Throwable error) {
        if (error != null) {
            getLogger().severe("Could not load player data: " + error.getMessage()
                    + " - HourGlass keeps running with an empty cache; nothing will be overwritten until"
                    + " you fix the problem.");
            return;
        }
        loadedPlayers = result == null ? 0 : result.loaded();
        playtime.markLoaded();
        for (Player player : getServer().getOnlinePlayers()) {
            tracking.start(player);           // re-anchor sessions that started mid-load
        }
        leaderboards.rebuild();
        if (config.debug()) {
            getLogger().info("[storage] " + (result == null ? "no files" : result.describe()));
        } else {
            getLogger().info("Loaded " + loadedPlayers + " player record(s) from players/.");
        }
        if (result != null && result.failed() > 0) {
            getLogger().warning(result.failed() + " player file(s) could not be read: "
                    + String.join(" | ", result.problems()));
        }
    }

    /** The one repeating timer. Every subsystem below decides for itself whether it is due. */
    private void tick() {
        if (!ticking.compareAndSet(false, true)) {
            return; // a stalled tick must never stack up behind itself
        }
        try {
            tracking.tick();
            leaderboards.tick();
            milestones.tick();
            display.tick();
            autosave();
        } catch (RuntimeException e) {
            getLogger().severe("HourGlass timer failed: " + e);
        } finally {
            ticking.set(false);
        }
    }

    private void autosave() {
        long minutes = config.autosaveMinutes();
        if (minutes <= 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextAutosaveMillis) {
            return;
        }
        nextAutosaveMillis = now + minutes * 60_000L;
        int queued = playtime.flushDirty();
        if (config.debug() && queued > 0) {
            getLogger().info("[storage] autosaved " + queued + " dirty record(s)");
        }
    }

    /**
     * Re-reads {@code config.yml}, {@code messages.yml} and {@code gui.yml}.
     *
     * @return {@code true} when everything loaded cleanly
     */
    public boolean reloadFiles() {
        boolean ok = true;
        org.bukkit.configuration.file.YamlConfiguration yaml = YamlIO.loadDefaults(this, "config.yml");
        try {
            config.load(yaml);
        } catch (RuntimeException e) {
            ok = false;
            getLogger().severe("config.yml could not be read: " + e);
        }
        try {
            messages.load();
        } catch (RuntimeException e) {
            ok = false;
            getLogger().severe("messages.yml could not be read: " + e);
        }
        try {
            guiConfig.load(YamlIO.loadDefaults(this, "gui.yml"));
        } catch (RuntimeException e) {
            ok = false;
            getLogger().severe("gui.yml could not be read: " + e);
        }
        sounds.load(yaml, config);

        for (Player player : getServer().getOnlinePlayers()) {
            var record = playtime.of(player.getUniqueId());
            if (record != null) {
                record.historySize(config.historySize());
            }
        }
        nextAutosaveMillis = System.currentTimeMillis() + Math.max(1L, config.autosaveMinutes()) * 60_000L;
        if (playtime.loaded()) {
            leaderboards.rebuild();
        }
        for (Player player : getServer().getOnlinePlayers()) {
            display.update(player);
        }
        return ok;
    }

    /** Public reload entry point used by {@code /playtime reload} and the GUI. */
    public boolean reload() {
        boolean ok = reloadFiles();
        // GUIs built from the old config are stale by definition.
        List<Player> copy = new ArrayList<>(getServer().getOnlinePlayers());
        for (Player player : copy) {
            gui.refresh(player);
        }
        return ok;
    }

    private void bind(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command '" + name + "' is missing from plugin.yml - /" + name + " will not work.");
            return;
        }
        if (executor instanceof org.bukkit.command.TabCompleter completer) {
            command.setTabCompleter(completer);
        }
        command.setExecutor(executor);
    }

    @Override
    public void onDisable() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (placeholdersRegistered) {
            try {
                PlaceholderApiHook.unregister(this);
            } catch (RuntimeException e) {
                getLogger().warning("Could not unregister PlaceholderAPI expansions: " + e.getMessage());
            }
            placeholdersRegistered = false;
        }
        try {
            display.clearAll();
        } catch (RuntimeException e) {
            getLogger().warning("Could not clear boss bars: " + e.getMessage());
        }
        try {
            if (config.flushOnDisable()) {
                for (var record : tracking.closeAll()) {
                    record.markDirty();
                }
                storage.flushNow(playtime.all());
            }
        } catch (RuntimeException e) {
            getLogger().severe("Final save failed: " + e);
        }
        storage.shutdown(10L);
        HourGlassApi.uninstall();
        getLogger().info("Saved playtime for " + playtime.size() + " player(s).");
    }

    // ------------------------------------------------------------------ getters

    public HourGlassConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public GuiConfig guiConfig() {
        return guiConfig;
    }

    public SoundService sounds() {
        return sounds;
    }

    public YamlPlayerStorage storage() {
        return storage;
    }

    public PlaytimeService playtime() {
        return playtime;
    }

    public TrackingService tracking() {
        return tracking;
    }

    public LeaderboardService leaderboards() {
        return leaderboards;
    }

    public MilestoneService milestones() {
        return milestones;
    }

    public DisplayService display() {
        return display;
    }

    public GuiService gui() {
        return gui;
    }

    /** Players loaded from disk at startup; handy for tests and {@code /playtimeadmin stats}. */
    public int loadedPlayers() {
        return loadedPlayers;
    }
}
