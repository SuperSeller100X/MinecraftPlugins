package dev.superseller.playerbank.config;

import dev.superseller.playerbank.PlayerBankPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public final class BankConfig {

    private final PlayerBankPlugin plugin;

    private boolean economyRequired;
    private boolean interestEnabled;
    private double ratePercent;
    private long intervalMillis;
    private double minBalance;
    private double maxBalance;
    private int decimalPlaces;
    private boolean notifyPlayers;
    private double minTransaction;
    private double maxDeposit;
    private double maxWithdraw;
    private int maxLogEntries;
    private int logPageSize;
    private String storageFile;
    private long autosaveSeconds;
    private int intervalHours;
    private int intervalMinutes;
    private int intervalSeconds;

    public BankConfig(PlayerBankPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration c = plugin.getConfig();
        economyRequired = c.getBoolean("economy.required", true);
        interestEnabled = c.getBoolean("bank.interest.enabled", true);
        ratePercent = c.getDouble("bank.interest.rate-percent", 2.5);
        intervalHours = Math.max(0, c.getInt("bank.interest.interval.hours", 0));
        intervalMinutes = Math.max(0, c.getInt("bank.interest.interval.minutes", 10));
        intervalSeconds = Math.max(0, c.getInt("bank.interest.interval.seconds", 0));
        long totalMs = (intervalHours * 3600L + intervalMinutes * 60L + intervalSeconds) * 1000L;
        if (totalMs < 1000L) {
            totalMs = 10 * 60 * 1000L;
            intervalMinutes = 10;
        }
        intervalMillis = totalMs;
        minBalance = c.getDouble("bank.interest.min-balance", 0.01);
        maxBalance = c.getDouble("bank.interest.max-balance", 0);
        decimalPlaces = Math.max(0, c.getInt("bank.interest.decimal-places", 2));
        notifyPlayers = c.getBoolean("bank.interest.notify-players", true);
        minTransaction = c.getDouble("bank.min-transaction", 0.01);
        maxDeposit = c.getDouble("bank.max-deposit", 0);
        maxWithdraw = c.getDouble("bank.max-withdraw", 0);
        maxLogEntries = Math.max(1, c.getInt("logs.max-entries", 100));
        logPageSize = Math.max(1, c.getInt("logs.page-size", 8));
        storageFile = c.getString("storage.file", "data.yml");
        autosaveSeconds = Math.max(0, c.getLong("storage.autosave-seconds", 60));
    }

    public boolean economyRequired() {
        return economyRequired;
    }

    public boolean interestEnabled() {
        return interestEnabled;
    }

    public double ratePercent() {
        return ratePercent;
    }

    public long intervalMillis() {
        return intervalMillis;
    }

    public long intervalTicks() {
        return Math.max(1L, intervalMillis / 50L);
    }

    public double minBalance() {
        return minBalance;
    }

    public double maxBalance() {
        return maxBalance;
    }

    public int decimalPlaces() {
        return decimalPlaces;
    }

    public boolean notifyPlayers() {
        return notifyPlayers;
    }

    public double minTransaction() {
        return minTransaction;
    }

    public double maxDeposit() {
        return maxDeposit;
    }

    public double maxWithdraw() {
        return maxWithdraw;
    }

    public int maxLogEntries() {
        return maxLogEntries;
    }

    public int logPageSize() {
        return logPageSize;
    }

    public String storageFile() {
        return storageFile;
    }

    public long autosaveSeconds() {
        return autosaveSeconds;
    }

    public String intervalDescription() {
        StringBuilder sb = new StringBuilder();
        if (intervalHours > 0) {
            sb.append(intervalHours).append("h ");
        }
        if (intervalMinutes > 0) {
            sb.append(intervalMinutes).append("m ");
        }
        if (intervalSeconds > 0) {
            sb.append(intervalSeconds).append("s");
        }
        String s = sb.toString().trim();
        return s.isEmpty() ? "10m" : s;
    }

    public double roundMoney(double value) {
        double factor = Math.pow(10, decimalPlaces);
        return Math.round(value * factor) / factor;
    }

    /** Rounds toward zero — used for "all"/"max" so the result never exceeds the source balance. */
    public double floorMoney(double value) {
        double factor = Math.pow(10, decimalPlaces);
        return Math.floor(value * factor) / factor;
    }
}
