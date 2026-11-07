package dev.superseller.veltryx;

import dev.superseller.veltryx.anticheat.AntiCheatService;
import dev.superseller.veltryx.anticheat.listener.AntiCheatListener;
import dev.superseller.veltryx.anticheat.xray.OreObfuscator;
import dev.superseller.veltryx.api.EconomyRegistry;
import dev.superseller.veltryx.command.EcoCommand;
import dev.superseller.veltryx.command.VeltryxCommand;
import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.economy.EconomyListener;
import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.punish.BanCommands;
import dev.superseller.veltryx.punish.BanService;
import dev.superseller.veltryx.punish.MuteCommand;
import dev.superseller.veltryx.punish.MuteService;
import dev.superseller.veltryx.punish.OffendCommand;
import dev.superseller.veltryx.punish.WarnCommand;
import dev.superseller.veltryx.punish.WarnService;
import dev.superseller.veltryx.staff.ChatListener;
import dev.superseller.veltryx.staff.FreezeService;
import dev.superseller.veltryx.staff.LookupCommand;
import dev.superseller.veltryx.staff.NotesCommand;
import dev.superseller.veltryx.staff.ReportCommand;
import dev.superseller.veltryx.staff.ReportService;
import dev.superseller.veltryx.staff.ReportsCommand;
import dev.superseller.veltryx.staff.StaffChatCommand;
import dev.superseller.veltryx.staff.StaffCommands;
import dev.superseller.veltryx.staff.VanishService;
import dev.superseller.veltryx.storage.DataStore;
import dev.superseller.veltryx.storage.SqliteDataStore;
import dev.superseller.veltryx.storage.YamlDataStore;
import dev.superseller.veltryx.sus.SusCommand;
import dev.superseller.veltryx.sus.OffendGui;
import dev.superseller.veltryx.sus.SusGui;
import dev.superseller.veltryx.util.PlatformScheduler;
import java.io.File;
import java.util.logging.Level;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Veltryx — the all-in-one server core: custom economy, anti-cheat with
 * anti-xray, custom category bans ({@code /offend}), the {@code /sus}
 * investigation GUI and staff tools.
 *
 * <p>Runs on Paper, Purpur and Folia for Minecraft 26.3 (Java 25). All
 * player-facing work is dispatched through {@link PlatformScheduler} so one
 * jar works on every platform. Storage is YAML or SQLite.
 */
public final class VeltryxPlugin extends JavaPlugin {

    private VeltryxConfig config;
    private Messages messages;
    private DataStore store;
    private EconomyService economy;
    private BanService bans;
    private MuteService mutes;
    private WarnService warns;
    private AntiCheatService anticheat;
    private OreObfuscator obfuscator;
    private VanishService vanish;
    private FreezeService freeze;
    private ChatListener chatListener;
    private SusGui susGui;
    private OffendGui offendGui;

    @Override
    public void onEnable() {
        String stage = "resource defaults";
        try {
            copyDefaults();

            stage = "scheduler init";
            PlatformScheduler.init(this);

            stage = "configuration";
            config = new VeltryxConfig(getConfig());
            messages = new Messages(this);
            messages.reload();

            stage = "storage";
            store = createDataStore();
            store.load();

            stage = "services";
            economy = new EconomyService(store, config);
            bans = new BanService(store, messages, config);
            mutes = new MuteService(store);
            warns = new WarnService(store, config.warnExpirySeconds());
            anticheat = new AntiCheatService(store, messages, config, bans);
            obfuscator = new OreObfuscator(config);
            vanish = new VanishService(this, config.joinVanishRemembers());
            freeze = new FreezeService();
            chatListener = new ChatListener(messages, mutes);
            chatListener.setSlowchatSeconds(0);
            ReportService reports = new ReportService(store, messages);
            offendGui = new OffendGui(config, messages, bans, anticheat);
            susGui = new SusGui(anticheat, bans, freeze, config, messages, offendGui);
            offendGui.attach(susGui);

            stage = "timers";
            EconomyRegistry.register(economy);
            anticheat.startTimer();
            freeze.startGuard();
            startAutosave();

            stage = "commands";
            registerCommands(reports);

            stage = "listeners";
            registerListeners(reports);

            getLogger().info("Veltryx " + getPluginMeta().getVersion() + " enabled on "
                    + (PlatformScheduler.isFolia() ? "Folia" : "Paper/Purpur")
                    + " using " + config.storageType() + " storage. Anti-cheat: "
                    + (config.anticheatEnabled() ? "on" : "off")
                    + ", xray obfuscation: " + (config.xrayObfuscation() ? "on" : "off") + ".");
        } catch (Throwable e) {
            getLogger().log(Level.SEVERE, "Veltryx failed to enable during stage '" + stage
                    + "'. Please report this error with the stack trace below.", e);
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (e instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * Copies the default config and messages out of the jar. If the jar was
     * repacked without its resources, log exactly what is missing instead of
     * crashing the whole plugin.
     */
    private void copyDefaults() {
        try {
            saveDefaultConfig();
        } catch (IllegalArgumentException e) {
            getLogger().severe("config.yml is missing from the plugin jar! Build the plugin with "
                    + "'mvn -B clean package' in Veltryx/ and use target/Veltryx-<version>.jar.");
        }
        try {
            saveResource("messages.yml", false);
        } catch (IllegalArgumentException e) {
            getLogger().severe("messages.yml is missing from the plugin jar! Build the plugin with "
                    + "'mvn -B clean package' in Veltryx/ and use target/Veltryx-<version>.jar. "
                    + "Messages will show as 'Missing message' until this is fixed.");
        }
    }

    @Override
    public void onDisable() {
        EconomyRegistry.unregister();
        if (store != null) {
            store.save();
            store.close();
        }
    }

    /** Reloads config, messages and the anti-cheat plans (/veltryx reload). */
    public void reloadEverything() {
        reloadConfig();
        config.reload(getConfig());
        messages.reload();
        anticheat.reload();
    }

    /** Name of the active storage backend. */
    public String storageTypeName() {
        return config.storageType();
    }

    private DataStore createDataStore() {
        File dataFolder = getDataFolder();
        if ("sqlite".equals(config.storageType())) {
            return new SqliteDataStore(new File(dataFolder, "veltryx.db"), getLogger());
        }
        return new YamlDataStore(dataFolder, getLogger());
    }

    private void registerCommands(ReportService reports) {
        EcoCommand ecoCommand = new EcoCommand(economy, messages, config);
        bind("eco", ecoCommand, ecoCommand);
        OffendCommand offendCommand = new OffendCommand(config, messages, bans, anticheat, offendGui);
        bind("offend", offendCommand, offendCommand);
        BanCommands banCommands = new BanCommands(messages, bans);
        bind("bans", banCommands, banCommands);
        bind("unban", banCommands, banCommands);
        SusCommand susCommand = new SusCommand(messages, susGui);
        bind("sus", susCommand, susCommand);
        LookupCommand lookupCommand = new LookupCommand(messages, store, economy, bans, mutes, warns, anticheat);
        bind("lookup", lookupCommand, lookupCommand);
        NotesCommand notesCommand = new NotesCommand(messages, store);
        bind("notes", notesCommand, notesCommand);
        WarnCommand warnCommand = new WarnCommand(messages, config, warns, mutes, bans);
        bind("warn", warnCommand, warnCommand);
        bind("warns", warnCommand, warnCommand);
        MuteCommand muteCommand = new MuteCommand(messages, config, mutes);
        bind("mute", muteCommand, muteCommand);
        bind("unmute", muteCommand, muteCommand);
        StaffChatCommand staffChatCommand = new StaffChatCommand(messages);
        bind("staffchat", staffChatCommand, staffChatCommand);
        ReportCommand reportCommand = new ReportCommand(messages, reports);
        bind("report", reportCommand, reportCommand);
        ReportsCommand reportsCommand = new ReportsCommand(messages, reports);
        bind("reports", reportsCommand, reportsCommand);
        StaffCommands staffCommands = new StaffCommands(messages, config, vanish, freeze, chatListener);
        bind("ping", staffCommands, staffCommands);
        bind("tps", staffCommands, staffCommands);
        bind("broadcast", staffCommands, staffCommands);
        bind("clearchat", staffCommands, staffCommands);
        bind("slowchat", staffCommands, staffCommands);
        bind("vanish", staffCommands, staffCommands);
        bind("freeze", staffCommands, staffCommands);
        VeltryxCommand veltryxCommand = new VeltryxCommand(this, messages);
        bind("veltryx", veltryxCommand, veltryxCommand);
    }

    private void registerListeners(ReportService reports) {
        register(new EconomyListener(store, economy, messages));
        register(new AntiCheatListener(anticheat, config, obfuscator));
        register(bans);
        register(chatListener);
        register(vanish);
        register(freeze);
        register(susGui);
        register(offendGui);
    }

    private void startAutosave() {
        PlatformScheduler.runTimer(() -> store.save(), Math.max(1L, config.autosaveSeconds() * 20L));
    }

    private void bind(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml!");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }

    private void register(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }
}
