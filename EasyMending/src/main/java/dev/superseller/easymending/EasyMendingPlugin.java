package dev.superseller.easymending;

import dev.superseller.easymending.command.EasyMendingAdminCommand;
import dev.superseller.easymending.command.EasyMendingCommand;
import dev.superseller.easymending.command.EasyMendingTabCompleter;
import dev.superseller.easymending.config.Messages;
import dev.superseller.easymending.config.PluginConfig;
import dev.superseller.easymending.gui.EasyMendingGui;
import dev.superseller.easymending.listener.GuiListener;
import dev.superseller.easymending.listener.PlayerInteractListener;
import dev.superseller.easymending.scheduler.PlatformScheduler;
import dev.superseller.easymending.service.RepairService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for EasyMending on Minecraft 26.2 (Paper / Purpur / Folia).
 */
public final class EasyMendingPlugin extends JavaPlugin {

    private static EasyMendingPlugin instance;

    private PluginConfig pluginConfig;
    private Messages messages;
    private RepairService repairService;
    private EasyMendingGui gui;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        PlatformScheduler.init(this);

        this.pluginConfig = new PluginConfig(this);
        this.pluginConfig.load();

        this.messages = new Messages(this);
        this.messages.load();

        this.repairService = new RepairService(this.pluginConfig);
        this.gui = new EasyMendingGui(this.pluginConfig, this.repairService);

        registerCommands();
        registerListeners();

        getLogger().info("EasyMending v" + getDescription().getVersion() + " initialized for Minecraft 26.2 (Paper/Purpur/Folia).");
    }

    @Override
    public void onDisable() {
        getLogger().info("EasyMending v" + getDescription().getVersion() + " disabled.");
        instance = null;
    }

    private void registerCommands() {
        EasyMendingAdminCommand adminCmd = new EasyMendingAdminCommand(pluginConfig, messages, repairService);
        EasyMendingCommand mainCmd = new EasyMendingCommand(pluginConfig, messages, repairService, gui, adminCmd);
        EasyMendingTabCompleter tabCompleter = new EasyMendingTabCompleter();

        PluginCommand main = getCommand("easymending");
        if (main != null) {
            main.setExecutor(mainCmd);
            main.setTabCompleter(tabCompleter);
        }

        PluginCommand admin = getCommand("easymendingadmin");
        if (admin != null) {
            admin.setExecutor(adminCmd);
            admin.setTabCompleter(tabCompleter);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new GuiListener(pluginConfig, messages, repairService, gui), this);
        getServer().getPluginManager().registerEvents(new PlayerInteractListener(pluginConfig, messages, repairService), this);
    }

    public static EasyMendingPlugin getInstance() {
        return instance;
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public Messages getMessages() {
        return messages;
    }

    public RepairService getRepairService() {
        return repairService;
    }

    public EasyMendingGui getGui() {
        return gui;
    }
}
