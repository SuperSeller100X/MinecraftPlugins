package dev.superseller.gifty;

import dev.superseller.gifty.command.GiftAdminCommand;
import dev.superseller.gifty.command.GiftCommand;
import dev.superseller.gifty.command.InboxCommand;
import dev.superseller.gifty.config.GiftyConfig;
import dev.superseller.gifty.economy.EconomyHook;
import dev.superseller.gifty.input.ChatInputManager;
import dev.superseller.gifty.listener.ChatListener;
import dev.superseller.gifty.listener.GuiListener;
import dev.superseller.gifty.listener.JoinQuitListener;
import dev.superseller.gifty.scheduler.PlatformScheduler;
import dev.superseller.gifty.storage.FileStorage;

import java.io.File;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Gifty — a sweet gift & delivery system with a DonutSMP-style GUI.
 * Paper / Purpur / Folia compatible (Folia-safe scheduling via reflection).
 */
public final class GiftyPlugin extends JavaPlugin {

    public static final String VERSION = "1.0.0";

    private GiftyConfig config;
    private FileStorage storage;
    private EconomyHook economy;
    private GuiSessionManager sessions;
    private ChatInputManager chatInput;

    @Override
    public void onEnable() {
        Logger logger = getLogger();
        logger.info("Enabling Gifty v" + VERSION + " ...");

        PlatformScheduler.init(this);

        config = new GiftyConfig(this);
        config.load();

        economy = new EconomyHook(this);
        economy.init(config.economyMode());

        storage = new FileStorage(new File(getDataFolder(), "data"), logger);

        chatInput = new ChatInputManager(config);
        sessions = new GuiSessionManager(config, storage, economy, chatInput);
        chatInput.setSessions(sessions);

        registerCommands();
        getServer().getPluginManager().registerEvents(new GuiListener(sessions, chatInput, config), this);
        getServer().getPluginManager().registerEvents(new ChatListener(chatInput), this);
        getServer().getPluginManager().registerEvents(new JoinQuitListener(sessions), this);

        hookPlaceholderApi();

        // periodic maintenance: timeouts, expiry scan (data-only) + autosave
        final long saveTicks = Math.max(20L, config.saveIntervalSeconds() * 20L);
        PlatformScheduler.runTimer(() -> {
            sessions.tick();
            storage.saveAll();
        }, saveTicks);

        logger.info("Gifty enabled. Slots per gift: " + config.sendSlots()
                + ", inbox pages: " + config.inboxPages()
                + ", economy: " + (economy.isEnabled() ? economy.providerName() : "disabled"));
    }

    private void registerCommands() {
        PluginCommand gift = getServer().getPluginCommand("gift");
        if (gift != null) {
            GiftCommand executor = new GiftCommand(config, sessions);
            gift.setExecutor(executor);
            gift.setTabCompleter(executor);
        }
        PluginCommand inbox = getServer().getPluginCommand("inbox");
        if (inbox != null) {
            InboxCommand executor = new InboxCommand(config, sessions);
            inbox.setExecutor(executor);
            inbox.setTabCompleter(executor);
        }
        PluginCommand admin = getServer().getPluginCommand("giftadmin");
        if (admin != null) {
            GiftAdminCommand executor = new GiftAdminCommand(this, config, sessions, economy);
            admin.setExecutor(executor);
            admin.setTabCompleter(executor);
        }
    }

    private void hookPlaceholderApi() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            if (Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion") != null) {
                new dev.superseller.gifty.papi.GiftyExpansion(storage, sessions).register();
                getLogger().info("PlaceholderAPI hook registered (%gifty_...%).");
            }
        } catch (Throwable t) {
            getLogger().warning("Could not register PlaceholderAPI expansion: " + t.getMessage());
        }
    }

    /** Reloads config/messages and re-detects economy. */
    public void reload() {
        config.load();
        economy.init(config.economyMode());
        getLogger().info("Gifty configuration reloaded.");
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.saveAll();
        }
        getLogger().info("Gifty disabled. All data saved.");
    }

    public GiftyConfig getGiftyConfig() {
        return config;
    }

    public GuiSessionManager getSessions() {
        return sessions;
    }

    public String version() {
        return VERSION;
    }
}
