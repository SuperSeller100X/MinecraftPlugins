package dev.superseller.justgambling.config;

import dev.superseller.justgambling.model.GameType;
import dev.superseller.justgambling.model.RiskProfile;
import dev.superseller.justgambling.model.RiskTier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Validated, reloadable settings facade. Unsafe configuration values are
 * clamped to usable values instead of being allowed to corrupt a wager.
 */
public final class PluginSettings {
    private final JavaPlugin plugin;
    private final EnumMap<RiskTier, RiskProfile> risks = new EnumMap<>(RiskTier.class);
    private final EnumMap<RiskTier, Integer> mineCounts = new EnumMap<>(RiskTier.class);
    private final EnumMap<RiskTier, Double> mineSteps = new EnumMap<>(RiskTier.class);
    private final EnumMap<GameType, Boolean> gameEnabled = new EnumMap<>(GameType.class);
    private List<Double> amountPresets = List.of(10.0, 50.0, 100.0, 500.0);

    private boolean fallbackEnabled;
    private String economyMode;
    private double fallbackStartingBalance;
    private String fallbackCurrency;
    private int decimalPlaces;
    private double minimumStake;
    private double maximumStake;
    private double maximumPayout;
    private long cooldownMillis;
    private int historySize;
    private double jackpotSeed;
    private double jackpotContribution;
    private double jackpotChance;
    private double jackpotMultiplier;
    private double lotteryMultiplier;
    private double lotteryContribution;
    private double crashHouseEdge;
    private double rouletteRedBlackPayout;
    private double rouletteGreenPayout;
    private String guiMainTitle;
    private String guiGameTitle;
    private String guiChoiceTitle;
    private String guiHistoryTitle;
    private String guiMinesTitle;
    private String guiAmountTitle;
    private String guiAnvilTitle;
    private float soundVolume;
    private float soundPitch;

    public PluginSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration c = plugin.getConfig();

        fallbackEnabled = c.getBoolean("economy.fallback.enabled", true);
        economyMode = nonBlank(c.getString("economy.mode", "auto"), "auto").toLowerCase(Locale.ROOT);
        if (!economyMode.equals("auto") && !economyMode.equals("vault") && !economyMode.equals("internal")) {
            economyMode = "auto";
        }
        fallbackStartingBalance = finiteNonNegative(c.getDouble("economy.fallback.starting-balance", 0.0), 0.0);
        fallbackCurrency = nonBlank(c.getString("economy.fallback.currency-name", "chips"), "chips");
        decimalPlaces = clamp(c.getInt("economy.decimal-places", 2), 0, 8);

        minimumStake = positiveFinite(c.getDouble("limits.minimum-stake", 0.01), 0.01);
        maximumStake = finiteNonNegative(c.getDouble("limits.maximum-stake", 0.0), 0.0);
        maximumPayout = finiteNonNegative(c.getDouble("limits.maximum-payout", 0.0), 0.0);
        double cooldownSeconds = finiteNonNegative(c.getDouble("limits.cooldown-seconds", 0.0), 0.0);
        cooldownMillis = Math.min(Long.MAX_VALUE, Math.round(cooldownSeconds * 1000.0));
        historySize = clamp(c.getInt("storage.max-history-per-player", 100), 10, 10_000);

        jackpotSeed = finiteNonNegative(c.getDouble("games.jackpot.seed", 1000.0), 1000.0);
        jackpotContribution = percentage(c.getDouble("games.jackpot.loss-contribution-percent", 5.0), 5.0);
        jackpotChance = chance(c.getDouble("games.jackpot.chance", 0.02), 0.02);
        jackpotMultiplier = positiveFinite(c.getDouble("games.jackpot.minimum-multiplier", 25.0), 25.0);
        lotteryMultiplier = positiveFinite(c.getDouble("games.lottery.payout-multiplier", 8.0), 8.0);
        lotteryContribution = percentage(c.getDouble("games.lottery.loss-contribution-percent", 2.0), 2.0);
        crashHouseEdge = percentage(c.getDouble("games.crash.house-edge-percent", 3.0), 3.0) / 100.0;
        rouletteRedBlackPayout = positiveFinite(c.getDouble("games.roulette.red-black-payout", 1.90), 1.90);
        rouletteGreenPayout = positiveFinite(c.getDouble("games.roulette.green-payout", 14.0), 14.0);

        risks.clear();
        for (RiskTier tier : RiskTier.values()) {
            String path = "risk-tiers." + tier.id();
            double chance = chance(c.getDouble(path + ".chance", defaultChance(tier)), defaultChance(tier));
            double multiplier = positiveFinite(c.getDouble(path + ".payout-multiplier", defaultMultiplier(tier)),
                    defaultMultiplier(tier));
            risks.put(tier, new RiskProfile(chance, multiplier));
        }

        mineCounts.clear();
        mineSteps.clear();
        for (RiskTier tier : RiskTier.values()) {
            mineCounts.put(tier, clamp(c.getInt("games.mines." + tier.id() + ".mines", defaultMines(tier)),
                    1, 24));
            mineSteps.put(tier, positiveFinite(c.getDouble("games.mines." + tier.id() + ".multiplier-per-safe",
                    defaultMineStep(tier)), defaultMineStep(tier)));
        }

        gameEnabled.clear();
        for (GameType type : GameType.values()) {
            gameEnabled.put(type, c.getBoolean("games." + type.id() + ".enabled", true));
        }

        List<Double> configuredPresets = new ArrayList<>();
        for (Object value : c.getList("gui.amount-presets", List.of(10, 50, 100, 500))) {
            if (value instanceof Number number && Double.isFinite(number.doubleValue()) && number.doubleValue() > 0) {
                configuredPresets.add(number.doubleValue());
            }
        }
        amountPresets = configuredPresets.isEmpty() ? List.of(10.0, 50.0, 100.0, 500.0) : List.copyOf(configuredPresets);

        guiMainTitle = nonBlank(c.getString("gui.titles.main", "<dark_gray>JustGambling <gold>Casino</gold>"),
                "<dark_gray>JustGambling <gold>Casino</gold>");
        guiGameTitle = nonBlank(c.getString("gui.titles.game", "<dark_gray>Play <gold>{game}</gold>"),
                "<dark_gray>Play <gold>{game}</gold>");
        guiChoiceTitle = nonBlank(c.getString("gui.titles.choice", "<dark_gray>Choose <gold>{game}</gold>"),
                "<dark_gray>Choose <gold>{game}</gold>");
        guiHistoryTitle = nonBlank(c.getString("gui.titles.history", "<dark_gray>Gambling history <gray>({page})</gray>"),
                "<dark_gray>Gambling history <gray>({page})</gray>");
        guiMinesTitle = nonBlank(c.getString("gui.titles.mines", "<dark_gray>Mines <gold>•</gold> {risk}"),
                "<dark_gray>Mines <gold>•</gold> {risk}");
        guiAmountTitle = nonBlank(c.getString("gui.titles.amount", "<dark_gray>Choose stake <gold>•</gold> {game}"),
                "<dark_gray>Choose stake <gold>•</gold> {game}");
        guiAnvilTitle = nonBlank(c.getString("gui.titles.anvil", "Enter a stake"), "Enter a stake");

        soundVolume = (float) bounded(c.getDouble("sounds.volume", 1.0), 0.0, 2.0, 1.0);
        soundPitch = (float) bounded(c.getDouble("sounds.pitch", 1.0), 0.5, 2.0, 1.0);
    }

    public RiskProfile risk(RiskTier tier) {
        return risks.getOrDefault(tier, new RiskProfile(0.5, 1.9));
    }

    public boolean isGameEnabled(GameType type) {
        return gameEnabled.getOrDefault(type, true);
    }

    public boolean fallbackEnabled() {
        return fallbackEnabled;
    }

    public String economyMode() {
        return economyMode;
    }

    public double fallbackStartingBalance() {
        return fallbackStartingBalance;
    }

    public String fallbackCurrency() {
        return fallbackCurrency;
    }

    public int decimalPlaces() {
        return decimalPlaces;
    }

    public double minimumStake() {
        return minimumStake;
    }

    public double maximumStake() {
        return maximumStake;
    }

    public double maximumPayout() {
        return maximumPayout;
    }

    public long cooldownMillis() {
        return cooldownMillis;
    }

    public int historySize() {
        return historySize;
    }

    public double jackpotSeed() {
        return jackpotSeed;
    }

    public double jackpotContribution() {
        return jackpotContribution;
    }

    public double jackpotChance() {
        return jackpotChance;
    }

    public double jackpotMultiplier() {
        return jackpotMultiplier;
    }

    public double lotteryMultiplier() {
        return lotteryMultiplier;
    }

    public double lotteryContribution() {
        return lotteryContribution;
    }

    public double crashHouseEdge() {
        return crashHouseEdge;
    }

    public double rouletteRedBlackPayout() {
        return rouletteRedBlackPayout;
    }

    public double rouletteGreenPayout() {
        return rouletteGreenPayout;
    }

    public int mineCount(RiskTier tier) {
        return mineCounts.getOrDefault(tier, 5);
    }

    public double mineStep(RiskTier tier) {
        return mineSteps.getOrDefault(tier, 0.2);
    }

    public List<Double> amountPresets() {
        return amountPresets;
    }

    public String guiMainTitle() {
        return guiMainTitle;
    }

    public String guiGameTitle() {
        return guiGameTitle;
    }

    public String guiChoiceTitle() {
        return guiChoiceTitle;
    }

    public String guiHistoryTitle() {
        return guiHistoryTitle;
    }

    public String guiMinesTitle() {
        return guiMinesTitle;
    }

    public String guiAmountTitle() {
        return guiAmountTitle;
    }

    public String guiAnvilTitle() {
        return guiAnvilTitle;
    }

    public float soundVolume() {
        return soundVolume;
    }

    public float soundPitch() {
        return soundPitch;
    }

    public String sound(String key, String fallback) {
        String value = plugin.getConfig().getString("sounds." + key, fallback);
        return value == null ? fallback : value.trim();
    }

    public boolean useSecureRandom() {
        return plugin.getConfig().getBoolean("games.use-secure-random", true);
    }

    public int autoSaveSeconds() {
        return clamp(plugin.getConfig().getInt("storage.autosave-seconds", 30), 5, 3600);
    }

    public boolean broadcastBigWins() {
        return plugin.getConfig().getBoolean("announcements.broadcast-big-wins", true);
    }

    public double broadcastThreshold() {
        return finiteNonNegative(plugin.getConfig().getDouble("announcements.minimum-broadcast-payout", 1000.0), 1000.0);
    }

    public Map<RiskTier, RiskProfile> riskProfiles() {
        return Map.copyOf(risks);
    }

    private static double defaultChance(RiskTier tier) {
        return switch (tier) {
            case SAFE -> 0.60;
            case BALANCED -> 0.50;
            case RISKY -> 0.25;
            case EXTREME -> 0.10;
        };
    }

    private static double defaultMultiplier(RiskTier tier) {
        return switch (tier) {
            case SAFE -> 1.45;
            case BALANCED -> 1.90;
            case RISKY -> 3.60;
            case EXTREME -> 8.50;
        };
    }

    private static int defaultMines(RiskTier tier) {
        return switch (tier) {
            case SAFE -> 3;
            case BALANCED -> 5;
            case RISKY -> 8;
            case EXTREME -> 12;
        };
    }

    private static double defaultMineStep(RiskTier tier) {
        return switch (tier) {
            case SAFE -> 0.12;
            case BALANCED -> 0.18;
            case RISKY -> 0.30;
            case EXTREME -> 0.55;
        };
    }

    private static double positiveFinite(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }

    private static double finiteNonNegative(double value, double fallback) {
        return Double.isFinite(value) && value >= 0.0 ? value : fallback;
    }

    private static double chance(double value, double fallback) {
        return bounded(value, 0.000001, 1.0, fallback);
    }

    private static double percentage(double value, double fallback) {
        return bounded(value, 0.0, 100.0, fallback);
    }

    private static double bounded(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
