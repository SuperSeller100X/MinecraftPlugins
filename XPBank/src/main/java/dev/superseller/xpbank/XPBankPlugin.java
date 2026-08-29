package dev.superseller.xpbank;

import dev.superseller.xpbank.bank.BankService;
import dev.superseller.xpbank.bank.InterestService;
import dev.superseller.xpbank.command.BankAdminCommand;
import dev.superseller.xpbank.command.BankCommand;
import dev.superseller.xpbank.config.Messages;
import dev.superseller.xpbank.config.XPBankConfig;
import dev.superseller.xpbank.gui.BankGui;
import dev.superseller.xpbank.listener.GuiListener;
import dev.superseller.xpbank.listener.PlayerJoinListener;
import dev.superseller.xpbank.scheduler.PlatformScheduler;
import dev.superseller.xpbank.storage.BankStorage;
import dev.superseller.xpbank.storage.SqliteStorage;
import dev.superseller.xpbank.storage.YamlStorage;

import java.io.File;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * XPBank — store total experience points (not levels) in a personal bank.
 *
 * <p>Runs on Paper, Purpur and Folia for Minecraft 26.2 (Java 25). All player
 * XP mutations are dispatched through {@link PlatformScheduler} to the owning
 * region thread, and persistence is a configurable YAML or SQLite backend.
 */
public final class XPBankPlugin extends JavaPlugin {

    private XPBankConfig bankConfig;
    private Messages messages;
    private BankStorage storage;
    private BankService bank;
    private BankGui gui;
    private InterestService interest;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);

        PlatformScheduler.init(this);

        bankConfig = new XPBankConfig(this);
        bankConfig.load();
        messages = new Messages(this);
        messages.load();

        storage = createStorage();
        storage.load();

        bank = new BankService(bankConfig, storage);
        gui = new BankGui(bankConfig, messages, bank);

        interest = new InterestService(this);
        interest.start();

        registerCommands();
        registerListeners();
        startAutosave();

        getLogger().info("XPBank enabled on " + (PlatformScheduler.isFolia() ? "Folia" : "Paper/Purpur")
                + " using " + bankConfig.storageType() + " storage. Transfers: "
                + (bankConfig.transfersEnabled() ? "on" : "off") + ", interest: "
                + (bankConfig.interestEnabled() ? "on" : "off") + ".");
    }

    @Override
    public void onDisable() {
        if (interest != null) {
            interest.stop();
        }
        if (storage != null) {
            storage.save();
            storage.close();
        }
    }

    private BankStorage createStorage() {
        File dataFolder = getDataFolder();
        if ("sqlite".equals(bankConfig.storageType())) {
            return new SqliteStorage(new File(dataFolder, "xpbank.db"), getLogger());
        }
        return new YamlStorage(new File(dataFolder, "balances.yml"), getLogger());
    }

    private void registerCommands() {
        BankCommand bankCommand = new BankCommand(this);
        PluginCommand xpbank = getCommand("xpbank");
        if (xpbank != null) {
            xpbank.setExecutor(bankCommand);
            xpbank.setTabCompleter(bankCommand);
        }
        BankAdminCommand adminCommand = new BankAdminCommand(this);
        PluginCommand xpbankadmin = getCommand("xpbankadmin");
        if (xpbankadmin != null) {
            xpbankadmin.setExecutor(adminCommand);
            xpbankadmin.setTabCompleter(adminCommand);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new GuiListener(this, bank, gui, messages), this);
        getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(storage), this);
    }

    private void startAutosave() {
        if (!bankConfig.autosaveEnabled()) {
            return;
        }
        long periodTicks = bankConfig.autosaveIntervalSeconds() * 20L;
        PlatformScheduler.runTimer(() -> PlatformScheduler.runAsync(() -> {
            if (storage != null) {
                storage.save();
            }
        }), periodTicks);
    }

    public void reloadAll() {
        reloadConfig();
        bankConfig.load();
        messages.load();

        // If the storage backend changed, migrate to the new one.
        String desired = bankConfig.storageType();
        boolean isYaml = storage instanceof YamlStorage;
        boolean wantYaml = desired.equals("yaml");
        if (isYaml != wantYaml) {
            getLogger().info("Storage backend changed to " + desired + "; reloading accounts.");
            storage.save();
            storage.close();
            storage = createStorage();
            storage.load();
            bank = new BankService(bankConfig, storage);
            gui = new BankGui(bankConfig, messages, bank);
            registerCommands();
        }

        // Apply any interest changes (enable/disable, rate, interval).
        if (interest != null) {
            interest.restart();
        }
    }

    public XPBankConfig bankConfig() {
        return bankConfig;
    }

    public Messages messages() {
        return messages;
    }

    public BankStorage storage() {
        return storage;
    }

    public BankService bank() {
        return bank;
    }

    public BankGui gui() {
        return gui;
    }

    public InterestService interest() {
        return interest;
    }
}
