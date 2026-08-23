package dev.superseller.playerbank;

import dev.superseller.playerbank.command.BankAdminCommand;
import dev.superseller.playerbank.command.BankCommand;
import dev.superseller.playerbank.config.BankConfig;
import dev.superseller.playerbank.config.Messages;
import dev.superseller.playerbank.economy.VaultHook;
import dev.superseller.playerbank.interest.InterestService;
import dev.superseller.playerbank.storage.BankStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerBankPlugin extends JavaPlugin {

    private BankConfig bankConfig;
    private Messages messages;
    private VaultHook vault;
    private BankStorage storage;
    private InterestService interest;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);

        bankConfig = new BankConfig(this);
        bankConfig.load();
        messages = new Messages(this);
        messages.load();

        vault = new VaultHook(this);
        vault.start();
        if (bankConfig.economyRequired() && !vault.isEnabled()) {
            getLogger().severe("No wallet economy yet (Vault or EssentialsX). Deposit/withdraw will retry when one is available.");
        }

        storage = new BankStorage(this, bankConfig);
        storage.load();

        interest = new InterestService(this);
        interest.start();

        BankCommand bankCommand = new BankCommand(this);
        PluginCommand bank = getCommand("bank");
        if (bank != null) {
            bank.setExecutor(bankCommand);
            bank.setTabCompleter(bankCommand);
        }
        BankAdminCommand admin = new BankAdminCommand(this);
        PluginCommand bankAdmin = getCommand("bankadmin");
        if (bankAdmin != null) {
            bankAdmin.setExecutor(admin);
            bankAdmin.setTabCompleter(admin);
        }

        getLogger().info("PlayerBank enabled. Interest " + bankConfig.ratePercent() + "% every "
                + bankConfig.intervalDescription() + ".");
    }

    @Override
    public void onDisable() {
        if (interest != null) {
            interest.stop();
        }
        if (storage != null) {
            storage.save();
        }
    }

    public void reloadAll() {
        reloadConfig();
        bankConfig.load();
        messages.load();
        vault.hook();
        storage.reloadPath();
        interest.restart();
    }

    public BankConfig bankConfig() {
        return bankConfig;
    }

    public Messages messages() {
        return messages;
    }

    public VaultHook vault() {
        return vault;
    }

    public BankStorage storage() {
        return storage;
    }

    public InterestService interest() {
        return interest;
    }
}
