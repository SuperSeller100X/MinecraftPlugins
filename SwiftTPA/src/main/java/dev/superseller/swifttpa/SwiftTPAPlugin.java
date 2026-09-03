package dev.superseller.swifttpa;

import dev.superseller.swifttpa.command.AdminCommand;
import dev.superseller.swifttpa.command.AbstractCommand;
import dev.superseller.swifttpa.command.SwiftTpaCommand;
import dev.superseller.swifttpa.command.TpAcceptCommand;
import dev.superseller.swifttpa.command.TpDenyCommand;
import dev.superseller.swifttpa.command.TpaBlockCommand;
import dev.superseller.swifttpa.command.TpaCancelCommand;
import dev.superseller.swifttpa.command.TpaCommand;
import dev.superseller.swifttpa.command.TpaHereCommand;
import dev.superseller.swifttpa.command.TpaListCommand;
import dev.superseller.swifttpa.command.TpaToggleCommand;
import dev.superseller.swifttpa.command.TpaUnblockCommand;
import dev.superseller.swifttpa.config.Messages;
import dev.superseller.swifttpa.config.SwiftTpaConfig;
import dev.superseller.swifttpa.gui.GuiListener;
import dev.superseller.swifttpa.gui.RequestsGui;
import dev.superseller.swifttpa.listener.PlayerSessionListener;
import dev.superseller.swifttpa.listener.WarmupListener;
import dev.superseller.swifttpa.request.TpaService;
import dev.superseller.swifttpa.scheduler.PlatformScheduler;
import dev.superseller.swifttpa.storage.PlayerDataStorage;
import dev.superseller.swifttpa.storage.SqliteStorage;
import dev.superseller.swifttpa.storage.YamlStorage;

import java.io.File;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * SwiftTPA — a fast teleport-request suite (/tpa, /tpahere, clickable accept
 * and deny, requests GUI, warmups, cooldowns, toggles, block lists and staff
 * tools) for Minecraft 26.2 running on Paper, Purpur or Folia (Java 25).
 *
 * <p>All player-facing work is dispatched through {@link PlatformScheduler}:
 * entity tasks touch players on their owning region thread, timers that only
 * manage plugin data run on the global thread, and disk/database I/O always
 * runs asynchronously — so a single jar is correct on every supported
 * platform, on Linux, Windows and macOS alike.
 */
public final class SwiftTPAPlugin extends JavaPlugin {

    private SwiftTpaConfig tpaConfig;
    private Messages messages;
    private PlayerDataStorage storage;
    private TpaService service;
    private RequestsGui gui;

    private PlatformScheduler.Cancellable expiryTask;
    private PlatformScheduler.Cancellable autosaveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);

        PlatformScheduler.init(this);

        tpaConfig = new SwiftTpaConfig(this);
        tpaConfig.load();
        messages = new Messages(this);
        messages.load();

        storage = createStorage();
        storage.init();

        service = new TpaService(this);
        gui = new RequestsGui(this);
        service.setGui(gui);

        registerCommands();
        registerListeners();
        startTimers();

        getLogger().info("SwiftTPA enabled on " + (PlatformScheduler.isFolia() ? "Folia" : "Paper/Purpur")
                + " using " + tpaConfig.storageType() + " storage. "
                + "Warmup: " + tpaConfig.warmupSeconds() + "s, cooldown: " + tpaConfig.cooldownSeconds() + "s, "
                + "expiry: " + tpaConfig.requestExpireSeconds() + "s.");
    }

    @Override
    public void onDisable() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        if (service != null) {
            service.warmups().cancelAll();
            service.store().clear();
        }
        if (storage != null) {
            storage.saveAll();
            storage.close();
        }
    }

    private PlayerDataStorage createStorage() {
        File dataFolder = getDataFolder();
        if ("sqlite".equals(tpaConfig.storageType())) {
            return new SqliteStorage(new File(dataFolder, "swifttpa.db"), getLogger());
        }
        return new YamlStorage(new File(dataFolder, "playerdata"), getLogger());
    }

    private void registerCommands() {
        bind("tpa", new TpaCommand(this));
        bind("tpahere", new TpaHereCommand(this));
        bind("tpaccept", new TpAcceptCommand(this));
        bind("tpdeny", new TpDenyCommand(this));
        bind("tpacancel", new TpaCancelCommand(this));
        bind("tpatoggle", new TpaToggleCommand(this));
        bind("tpalist", new TpaListCommand(this));
        bind("tpablock", new TpaBlockCommand(this));
        bind("tpaunblock", new TpaUnblockCommand(this));
        bind("swifttpa", new SwiftTpaCommand(this));
        bind("swifttpaadmin", new AdminCommand(this));
    }

    private void bind(String name, AbstractCommand executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml — skipped.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerSessionListener(this), this);
        getServer().getPluginManager().registerEvents(new WarmupListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this, gui), this);
    }

    private void startTimers() {
        expiryTask = PlatformScheduler.runTimerCancellable(() -> service.expireSweep(), 20L);
        startAutosave();
    }

    private void startAutosave() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        if (!tpaConfig.autosaveEnabled()) {
            return;
        }
        long periodTicks = tpaConfig.autosaveIntervalSeconds() * 20L;
        autosaveTask = PlatformScheduler.runTimerCancellable(
                () -> PlatformScheduler.runAsync(() -> {
                    if (storage != null) {
                        storage.saveAll();
                    }
                }), periodTicks);
    }

    /** Reloads config.yml, messages.yml and reapplies live-reloadable settings. */
    public void reloadAll() {
        tpaConfig.load();
        messages.load();

        // If the storage backend changed, switch to the new one.
        String desired = tpaConfig.storageType();
        boolean wantYaml = !desired.equals("sqlite");
        boolean isYaml = storage instanceof YamlStorage;
        if (isYaml != wantYaml) {
            getLogger().info("Storage backend changed to " + desired + " — migrating cache.");
            storage.saveAll();
            storage.close();
            storage = createStorage();
            storage.init();
        }

        // Apply autosave interval changes.
        startAutosave();
    }

    public SwiftTpaConfig tpaConfig() {
        return tpaConfig;
    }

    public Messages messages() {
        return messages;
    }

    public PlayerDataStorage storage() {
        return storage;
    }

    public TpaService service() {
        return service;
    }

    public RequestsGui gui() {
        return gui;
    }
}
