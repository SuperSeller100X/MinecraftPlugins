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
import dev.superseller.veltryx.home.HomeCommand;
import dev.superseller.veltryx.home.HomeService;
import dev.superseller.veltryx.home.HomesGui;
import dev.superseller.veltryx.market.AhCommand;
import dev.superseller.veltryx.market.AhGui;
import dev.superseller.veltryx.market.AhService;
import dev.superseller.veltryx.market.OrderCommand;
import dev.superseller.veltryx.market.OrderGui;
import dev.superseller.veltryx.market.OrderService;
import dev.superseller.veltryx.teleport.TpaCommand;
import dev.superseller.veltryx.teleport.TpaService;
import dev.superseller.veltryx.punish.BanCommands;
import dev.superseller.veltryx.punish.BanCommand;
import dev.superseller.veltryx.punish.BanService;
import dev.superseller.veltryx.punish.MuteCommand;
import dev.superseller.veltryx.punish.MuteService;
import dev.superseller.veltryx.punish.OffendCommand;
import dev.superseller.veltryx.punish.WarnCommand;
import dev.superseller.veltryx.punish.WarnService;
import dev.superseller.veltryx.shop.SellCommand;
import dev.superseller.veltryx.shop.SellGui;
import dev.superseller.veltryx.shop.ShopCommand;
import dev.superseller.veltryx.shop.ShopGui;
import dev.superseller.veltryx.shop.ShopService;
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
    private ShopService shopService;
    private HomeService homeService;
    private HomesGui homesGui;
    private TpaService tpaService;
    private AhService ahService;
    private AhGui ahGui;
    private OrderService orderService;
    private OrderGui orderGui;
    private ShopGui shopGui;
    private SellGui sellGui;

    @Override
    public void onEnable() {
        String stage = "resource defaults";
        try {
            syncResources();

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
            shopService = new ShopService(economy, getDataFolder().toPath().resolve("shop.yml"));
            shopGui = new ShopGui(shopService, messages);
            sellGui = new SellGui(shopService, messages);
            homeService = new HomeService(store, economy, messages, config);
            homesGui = new HomesGui(homeService, economy, messages, config);
            tpaService = new TpaService();
            ahService = new AhService(store, economy, messages, config);
            ahGui = new AhGui(ahService, economy, messages);
            orderService = new OrderService(store, economy, messages, config);
            orderGui = new OrderGui(orderService, economy, messages);

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
    /**
     * Creates or updates config.yml and messages.yml from the jar templates:
     * keys added by a plugin update appear automatically with their defaults,
     * while every value the user changed is kept exactly as it is.
     */
    public void syncResources() {
        mergeResource("config.yml");
        mergeResource("messages.yml");
        mergeResource("shop.yml");
    }

    private void mergeResource(String name) {
        File file = new File(getDataFolder(), name);
        String template = readResource(name);
        if (template == null) {
            getLogger().severe(name + " is missing from the plugin jar! Build the plugin with "
                    + "'mvn -B clean package' in Veltryx/ and use target/Veltryx-<version>.jar.");
            return;
        }
        try {
            getDataFolder().mkdirs();
            if (!file.exists()) {
                java.nio.file.Files.writeString(file.toPath(), template);
                return;
            }
            String user = java.nio.file.Files.readString(file.toPath());
            String merged = dev.superseller.veltryx.util.YamlTextMerge.merge(template, user);
            if (!merged.equals(user)) {
                java.nio.file.Files.writeString(file.toPath(), merged);
                getLogger().info("Updated " + name + " with new default settings (your values were kept).");
            }
        } catch (java.io.IOException e) {
            getLogger().log(Level.SEVERE, "Could not update " + name, e);
        }
    }

    private String readResource(String name) {
        try (java.io.InputStream in = getResource(name)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return null;
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
        syncResources();
        if (shopService != null) {
            shopService.reload();
        }
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
        ShopCommand shopCommand = new ShopCommand(shopGui, shopService, messages);
        bind("shop", shopCommand, shopCommand);
        SellCommand sellCommand = new SellCommand(sellGui, messages);
        bind("sell", sellCommand, sellCommand);
        AhCommand ahCommand = new AhCommand(ahGui, ahService, messages);
        bind("ah", ahCommand, ahCommand);
        OrderCommand orderCommand = new OrderCommand(orderGui, orderService, messages);
        bind("order", orderCommand, orderCommand);
        bind("orders", orderCommand, orderCommand);
        HomeCommand homeCommand = new HomeCommand(homeService, homesGui, messages, config);
        bind("home", homeCommand, homeCommand);
        bind("sethome", homeCommand, homeCommand);
        bind("homes", homeCommand, homeCommand);
        TpaCommand tpaCommand = new TpaCommand(tpaService, messages, config);
        bind("tpa", tpaCommand, tpaCommand);
        bind("tpahere", tpaCommand, tpaCommand);
        bind("tp", tpaCommand, tpaCommand);
        bind("tphere", tpaCommand, tpaCommand);
        OffendCommand offendCommand = new OffendCommand(config, messages, bans, anticheat, offendGui);
        bind("offend", offendCommand, offendCommand);
        BanCommand banCommand = new BanCommand(messages, bans, anticheat);
        bind("ban", banCommand, banCommand);
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
        register(shopGui);
        register(sellGui);
        register(homesGui);
        register(ahGui);
        register(orderGui);
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
