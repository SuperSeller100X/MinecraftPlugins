package dev.superseller.combattag;

import dev.superseller.combattag.api.CombatTagApi;
import dev.superseller.combattag.combat.CombatManager;
import dev.superseller.combattag.combat.RestrictionService;
import dev.superseller.combattag.command.CombatTabCompleter;
import dev.superseller.combattag.command.CombatTagAdminCommand;
import dev.superseller.combattag.command.CombatTagCommand;
import dev.superseller.combattag.config.Messages;
import dev.superseller.combattag.config.PluginConfig;
import dev.superseller.combattag.display.DisplayManager;
import dev.superseller.combattag.gui.CombatGui;
import dev.superseller.combattag.listener.CombatListener;
import dev.superseller.combattag.listener.CommandListener;
import dev.superseller.combattag.listener.ConnectionListener;
import dev.superseller.combattag.listener.EasyMendingListener;
import dev.superseller.combattag.listener.GuiListener;
import dev.superseller.combattag.listener.InventoryListener;
import dev.superseller.combattag.listener.TeleportListener;
import dev.superseller.combattag.scheduler.PlatformScheduler;
import dev.superseller.combattag.util.SoundUtil;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * CombatTag — PvP combat tagging for Minecraft 26.2 (Paper / Purpur / Folia, Java 25).
 */
public final class CombatTagPlugin extends JavaPlugin {

    private static CombatTagPlugin instance;

    private PluginConfig pluginConfig;
    private Messages messages;
    private CombatManager combatManager;
    private RestrictionService restrictions;
    private DisplayManager display;
    private CombatGui gui;
    private CombatTagApi api;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        PlatformScheduler.init(this);

        this.pluginConfig = new PluginConfig(this);
        this.pluginConfig.load();

        this.messages = new Messages(this);
        this.messages.load();

        this.combatManager = new CombatManager(pluginConfig);
        this.restrictions = new RestrictionService(this, pluginConfig, messages, combatManager);
        this.display = new DisplayManager(pluginConfig, messages, combatManager);
        this.gui = new CombatGui(pluginConfig, messages, combatManager);
        this.api = new CombatTagApi(combatManager);

        combatManager.setCallbacks(
                (player, entry) -> {
                    messages.send(player, "combat.tag-start",
                            "seconds", String.valueOf(entry.remainingSeconds(System.currentTimeMillis())));
                    SoundUtil.play(player, pluginConfig.getSoundTagStart(), pluginConfig.isSoundsEnabled());
                    display.showTagTitle(player, entry);
                },
                (uuid, entry) -> {
                    display.clear(uuid);
                    org.bukkit.entity.Player player = getServer().getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        messages.send(player, "combat.tag-end");
                        SoundUtil.play(player, pluginConfig.getSoundTagEnd(), pluginConfig.isSoundsEnabled());
                    }
                });

        registerCommands();
        registerListeners();
        registerIntegrations();
        display.start();

        getLogger().info("CombatTag v" + getPluginMeta().getVersion()
                + " enabled for Minecraft 26.2 (Paper / Purpur / Folia).");
    }

    @Override
    public void onDisable() {
        if (display != null) {
            display.stop();
        }
        if (combatManager != null) {
            combatManager.untagAll();
        }
        getLogger().info("CombatTag disabled.");
        instance = null;
    }

    /** Reloads config.yml and messages.yml and restarts the display ticker. */
    public void reloadEverything() {
        pluginConfig.load();
        messages.load();
        display.stop();
        display.start();
    }

    private void registerCommands() {
        CombatTagCommand playerCommand = new CombatTagCommand(pluginConfig, messages, combatManager, gui);
        CombatTagAdminCommand adminCommand =
                new CombatTagAdminCommand(this, pluginConfig, messages, combatManager, gui);
        CombatTabCompleter completer = new CombatTabCompleter();

        PluginCommand main = getCommand("combattag");
        if (main != null) {
            main.setExecutor(playerCommand);
            main.setTabCompleter(completer);
        }
        PluginCommand admin = getCommand("combattagadmin");
        if (admin != null) {
            admin.setExecutor(adminCommand);
            admin.setTabCompleter(completer);
        }
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new CombatListener(pluginConfig, combatManager), this);
        pm.registerEvents(new CommandListener(pluginConfig, combatManager, restrictions), this);
        pm.registerEvents(new TeleportListener(pluginConfig, combatManager, restrictions), this);
        pm.registerEvents(new InventoryListener(pluginConfig, combatManager, restrictions), this);
        pm.registerEvents(new EasyMendingListener(pluginConfig, combatManager, restrictions), this);
        pm.registerEvents(new ConnectionListener(this, pluginConfig, messages, combatManager, display), this);
        pm.registerEvents(new GuiListener(this, pluginConfig, messages, combatManager, gui), this);
    }

    private void registerIntegrations() {
        if (!pluginConfig.isPlaceholderApiEnabled()) {
            return;
        }
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            new dev.superseller.combattag.integration.PlaceholderApiHook(this, combatManager).register();
            getLogger().info("Registered the PlaceholderAPI expansion (%combattag_...%).");
        } catch (Throwable t) {
            getLogger().warning("Could not register PlaceholderAPI expansion: " + t.getMessage());
        }
    }

    public static CombatTagPlugin getInstance() {
        return instance;
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public Messages getMessages() {
        return messages;
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }

    public CombatTagApi getApi() {
        return api;
    }
}
