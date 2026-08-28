package dev.superseller.playervault;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import dev.superseller.playervault.command.VaultAdminCommand;
import dev.superseller.playervault.command.VaultCommand;
import dev.superseller.playervault.command.VaultTabCompleter;
import dev.superseller.playervault.config.Messages;
import dev.superseller.playervault.config.Settings;
import dev.superseller.playervault.economy.EconomyHook;
import dev.superseller.playervault.gui.GuiListener;
import dev.superseller.playervault.gui.VaultGui;
import dev.superseller.playervault.gui.VaultHolder;
import dev.superseller.playervault.integration.VaultPlaceholders;
import dev.superseller.playervault.listener.ConnectionListener;
import dev.superseller.playervault.scheduler.PlatformScheduler;
import dev.superseller.playervault.service.VaultService;
import dev.superseller.playervault.storage.SqliteVaultStore;
import dev.superseller.playervault.storage.VaultStore;
import dev.superseller.playervault.storage.YamlVaultStore;

/**
 * PlayerVault — a personal, expandable storage vault for every player.
 *
 * <p>Targets Minecraft <strong>26.2</strong> on Paper, Purpur and Folia, built with
 * Java 25. All scheduling goes through {@link PlatformScheduler}, all persistence
 * through {@link VaultStore}, and all economy access through a reflective hook, so
 * the plugin runs with or without Vault, VaultUnlocked and PlaceholderAPI.
 */
public final class PlayerVaultPlugin extends JavaPlugin {

    private static final String AUTHOR = "SuperSeller100X";

    private Settings settings;
    private Messages messages;
    private PlatformScheduler scheduler;
    private EconomyHook economy;
    private VaultStore store;
    private VaultService service;
    private VaultGui gui;
    private GuiListener guiListener;
    private Runnable autoSaveCancel;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        reloadConfig();

        settings = Settings.load(this);
        messages = Messages.load(messagesFile(), getLogger());
        scheduler = new PlatformScheduler(this);

        economy = new EconomyHook(this);
        economy.init(settings.economy().enabled());

        store = createStore();
        service = new VaultService(this, messages, scheduler, economy, () -> settings);
        service.store(store);

        gui = new VaultGui(messages, service, () -> settings);
        guiListener = new GuiListener(messages, service, gui, () -> settings);
        service.afterUpgrade(gui::repaint);

        getServer().getPluginManager().registerEvents(guiListener, this);
        getServer().getPluginManager().registerEvents(
                new ConnectionListener(service, guiListener, scheduler, () -> settings), this);

        registerCommands();
        registerPlaceholders();
        startAutoSave();

        for (Player player : Bukkit.getOnlinePlayers()) {
            service.preload(player);
        }

        getLogger().info("PlayerVault enabled — storage: " + store.name()
                + ", economy: " + (economy.isEnabled() ? economy.providerName() : "none")
                + ", folia: " + scheduler.folia());
    }

    @Override
    public void onDisable() {
        if (scheduler != null) {
            scheduler.cancel();
        }
        if (service != null) {
            // Inventories are not closed by every fork during shutdown, so read any
            // open vault back before writing the cache to disk.
            if (guiListener != null) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Inventory open = player.getOpenInventory().getTopInventory();
                    VaultHolder holder = VaultGui.holder(open);
                    if (holder != null) {
                        guiListener.collect(holder, open);
                    }
                }
            }
            int written = service.saveAll();
            getLogger().info("Saved " + written + " vault(s).");
        }
        if (store != null) {
            store.close();
        }
    }

    private File messagesFile() {
        return new File(getDataFolder(), "messages.yml");
    }

    private VaultStore createStore() {
        Settings.Storage config = settings.storage();
        int startingRows = settings.vault().startingRows();
        if (config.backend() == Settings.Backend.SQLITE) {
            SqliteVaultStore sqlite = new SqliteVaultStore(getDataFolder(), getLogger(), startingRows,
                    config.journalMode(), config.busyTimeoutMs());
            try {
                sqlite.open();
                return sqlite;
            } catch (IOException ex) {
                getLogger().log(Level.SEVERE, "SQLite storage is unavailable, falling back to YAML.", ex);
            }
        }
        return new YamlVaultStore(getDataFolder(), getLogger(), startingRows);
    }

    private void registerCommands() {
        VaultTabCompleter completer = new VaultTabCompleter();
        bind("playervault",
                new VaultCommand(this, messages, service, gui, () -> settings, this::reloadAll), completer);
        bind("playervaultadmin",
                new VaultAdminCommand(this, messages, service, gui, () -> settings, this::reloadAll), completer);
    }

    private void bind(String name, org.bukkit.command.CommandExecutor executor,
                      org.bukkit.command.TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }

    private void registerPlaceholders() {
        if (!settings.placeholders()) {
            return;
        }
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        Package owner = getClass().getPackage();
        String version = owner == null ? null : owner.getImplementationVersion();
        VaultPlaceholders expansion =
                new VaultPlaceholders(service, () -> settings, AUTHOR, version == null ? "1.0.0" : version);
        if (expansion.register()) {
            getLogger().info("Registered PlaceholderAPI placeholders (%playervault_*%).");
        }
    }

    private void startAutoSave() {
        int seconds = settings.storage().autoSaveSeconds();
        if (seconds <= 0) {
            getLogger().info("Auto-save is disabled.");
            return;
        }
        if (autoSaveCancel != null) {
            autoSaveCancel.run();
            autoSaveCancel = null;
        }
        autoSaveCancel = scheduler.runTimerAsync(() -> {
            int written = service.saveAll();
            if (settings.debug()) {
                getLogger().info("Auto-saved " + written + " vault(s).");
            }
        }, seconds * 20L);
        getLogger().info("Auto-saving every " + seconds + "s.");
    }

    /** Re-reads {@code config.yml} and {@code messages.yml}, swapping storage if needed. */
    private void reloadAll() {
        reloadConfig();
        settings = Settings.load(this);
        messages.reload(messagesFile(), getLogger());
        economy.init(settings.economy().enabled());
        switchStoreIfNeeded();
        startAutoSave();
    }

    /**
     * Moves to a different storage backend when the config asks for one.
     *
     * <p>The in-memory cache is what carries the vaults across: it is written to the
     * new backend immediately, so a reload never loses items when the format changes.
     */
    private void switchStoreIfNeeded() {
        VaultStore next = createStore();
        if (next.name().equals(store.name())) {
            next.close();
            return;
        }
        VaultStore previous = store;
        store = next;
        service.store(store);
        // Write to the NEW backend: the cache stays in memory, so this is what
        // actually carries the vaults across. Flushing to the old one would leave
        // the new backend empty and orphan everything just written.
        int written = service.saveAll();
        previous.close();
        getLogger().info("Storage backend switched to " + store.name()
                + "; carried " + written + " vault(s) over.");
    }
}
