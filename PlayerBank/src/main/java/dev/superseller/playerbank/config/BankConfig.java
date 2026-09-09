package dev.superseller.playerbank.config;

import dev.superseller.playerbank.PlayerBankPlugin;
import dev.superseller.playerbank.gui.Amount;
import dev.superseller.playerbank.gui.AmountParser;
import dev.superseller.playerbank.gui.ChestLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
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

    private String guiType;
    private boolean guiPlayerChoice;
    private final List<Amount> quickAmounts = new ArrayList<>();
    private int chestRows;
    private String chestTitle;
    private Material chestFiller;
    private boolean soundsEnabled;
    private String soundOpen;
    private String soundClick;
    private String soundDeposit;
    private String soundWithdraw;
    private String soundError;

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
        loadGui(c);
    }

    private void loadGui(FileConfiguration c) {
        guiType = c.getString("gui.type", "AUTO").trim().toUpperCase(Locale.ROOT);
        if (!guiType.equals("AUTO") && !guiType.equals("CHEST") && !guiType.equals("DIALOG")) {
            plugin.getLogger().warning("Unknown gui.type '" + guiType + "', using AUTO.");
            guiType = "AUTO";
        }
        guiPlayerChoice = c.getBoolean("gui.player-choice", true);

        quickAmounts.clear();
        for (String raw : c.getStringList("gui.quick-amounts")) {
            Amount amount = AmountParser.parse(raw);
            if (amount == null) {
                plugin.getLogger().warning("Skipping invalid gui.quick-amounts entry '" + raw + "'.");
                continue;
            }
            if (quickAmounts.size() >= ChestLayout.MAX_QUICK_BUTTONS) {
                plugin.getLogger().warning("gui.quick-amounts holds more than "
                        + ChestLayout.MAX_QUICK_BUTTONS + " entries; extra ones are ignored.");
                break;
            }
            quickAmounts.add(amount);
        }
        if (quickAmounts.isEmpty()) {
            quickAmounts.add(AmountParser.parse("1000"));
            quickAmounts.add(AmountParser.parse("all"));
        }

        chestRows = ChestLayout.clampRows(c.getInt("gui.chest.rows", 4));
        chestTitle = c.getString("gui.chest.title", "<dark_gray>Bank");
        chestFiller = material(c.getString("gui.chest.filler-material", "GRAY_STAINED_GLASS_PANE"),
                Material.GRAY_STAINED_GLASS_PANE);

        soundsEnabled = c.getBoolean("gui.sounds.enabled", true);
        soundOpen = c.getString("gui.sounds.open", "BLOCK_ENDER_CHEST_OPEN");
        soundClick = c.getString("gui.sounds.click", "UI_BUTTON_CLICK");
        soundDeposit = c.getString("gui.sounds.deposit", "ENTITY_EXPERIENCE_ORB_PICKUP");
        soundWithdraw = c.getString("gui.sounds.withdraw", "ENTITY_PLAYER_LEVELUP");
        soundError = c.getString("gui.sounds.error", "ENTITY_VILLAGER_NO");
    }

    private Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material m = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        if (m == null) {
            plugin.getLogger().warning("Unknown material '" + name + "', using " + fallback + ".");
            return fallback;
        }
        return m;
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

    // --- GUI settings ---

    /** Configured menu backend: AUTO, CHEST or DIALOG (upper-case). */
    public String guiType() {
        return guiType;
    }

    /** Whether players may pick their own style with {@code /bank gui <style>}. */
    public boolean guiPlayerChoice() {
        return guiPlayerChoice;
    }

    /** Parsed quick-amount buttons (max {@link ChestLayout#MAX_QUICK_BUTTONS}). */
    public List<Amount> quickAmounts() {
        return List.copyOf(quickAmounts);
    }

    /** Chest menu rows, clamped to 4..6. */
    public int chestRows() {
        return chestRows;
    }

    /** Chest menu title (MiniMessage). */
    public String chestTitle() {
        return chestTitle;
    }

    public Material chestFiller() {
        return chestFiller;
    }

    /** Icon material for a chest menu button; falls back to a sensible default. */
    public Material chestIcon(String key) {
        Material fallback = defaultIcon(key);
        String raw = plugin.getConfig().getString("gui.chest.icons." + key);
        return raw == null || raw.isBlank() ? fallback : material(raw, fallback);
    }

    private Material defaultIcon(String key) {
        return switch (key) {
            case "info" -> Material.GOLD_INGOT;
            case "deposit" -> Material.EMERALD;
            case "deposit-all" -> Material.EMERALD_BLOCK;
            case "withdraw" -> Material.GOLD_NUGGET;
            case "withdraw-all" -> Material.GOLD_BLOCK;
            case "interest" -> Material.CLOCK;
            case "logs" -> Material.BOOK;
            case "log-entry" -> Material.PAPER;
            case "close" -> Material.BARRIER;
            case "style" -> Material.COMPARATOR;
            case "previous-page" -> Material.ARROW;
            case "next-page" -> Material.ARROW;
            default -> Material.GRAY_STAINED_GLASS_PANE;
        };
    }

    public boolean soundsEnabled() {
        return soundsEnabled;
    }

    /**
     * Sound for a GUI event key, or null when blank/disabled/unknown. Accepts
     * both the key form ({@code ui.button.click}, {@code custom:pack.sound})
     * and the legacy enum spelling ({@code UI_BUTTON_CLICK}).
     */
    public Sound sound(String key) {
        String name = switch (key) {
            case "open" -> soundOpen;
            case "click" -> soundClick;
            case "deposit" -> soundDeposit;
            case "withdraw" -> soundWithdraw;
            case "error" -> soundError;
            default -> null;
        };
        if (!soundsEnabled || name == null || name.isBlank() || "none".equalsIgnoreCase(name)) {
            return null;
        }
        Sound resolved = lookupSound(name);
        if (resolved == null) {
            plugin.getLogger().warning("Unknown sound '" + name + "' (gui.sounds." + key + ").");
        }
        return resolved;
    }

    /**
     * Resolves a sound name through the sound registry — {@link Sound#valueOf}
     * is deprecated for removal. Falls back to comparing the legacy enum
     * spelling with separators stripped, because enum names do not map 1:1
     * onto key names ({@code UI_BUTTON_CLICK} is {@code ui.button.click}).
     */
    private Sound lookupSound(String raw) {
        String name = raw.trim().toLowerCase(Locale.ROOT);
        try {
            NamespacedKey key = name.indexOf(':') >= 0
                    ? NamespacedKey.fromString(name)
                    : NamespacedKey.minecraft(name);
            if (key != null) {
                Sound direct = Registry.SOUNDS.get(key);
                if (direct != null) {
                    return direct;
                }
            }
            String wanted = name.replace("_", "").replace(".", "");
            for (Sound sound : Registry.SOUNDS) {
                NamespacedKey soundKey = Registry.SOUNDS.getKey(sound);
                if (soundKey != null
                        && soundKey.value().replace("_", "").replace(".", "").equals(wanted)) {
                    return sound;
                }
            }
        } catch (Throwable t) {
            // A bad sound name must never break a reload.
        }
        return null;
    }
}
