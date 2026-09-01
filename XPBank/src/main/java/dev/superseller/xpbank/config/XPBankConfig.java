package dev.superseller.xpbank.config;

import dev.superseller.xpbank.XPBankPlugin;

import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed access to config.yml. Everything the plugin does is configurable:
 * storage backend, feature toggles, GUI layout and every sound effect.
 */
public final class XPBankConfig {

    private final XPBankPlugin plugin;

    private String storageType;
    private boolean transfersEnabled;
    private boolean leaderboardEnabled;
    private int leaderboardSize;
    private long minTransfer;
    private long minDeposit;
    private long minWithdraw;
    private boolean autosaveEnabled;
    private int autosaveIntervalSeconds;
    private boolean depositKeepXpBar;

    private boolean interestEnabled;
    private double interestRatePercent;
    private int interestIntervalHours;
    private int interestIntervalMinutes;
    private int interestIntervalSeconds;
    private long interestMinBalance;
    private long interestMaxPayout;
    private boolean interestOfflinePlayers;
    private boolean interestNotify;
    private String soundInterest;

    private boolean soundsEnabled;
    private String soundDeposit;
    private String soundWithdraw;
    private String soundTransfer;
    private String soundError;
    private String soundGuiOpen;
    private String soundGuiClick;

    private String guiTitle;
    private int guiRows;
    private Material guiBalanceIcon;
    private Material guiDepositIcon;
    private Material guiWithdrawIcon;
    private Material guiFiller;

    public XPBankConfig(XPBankPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration c = plugin.getConfig();

        storageType = c.getString("storage.type", "yaml").toLowerCase(Locale.US);
        if (!storageType.equals("yaml") && !storageType.equals("sqlite")) {
            plugin.getLogger().warning("Unknown storage.type '" + storageType + "', defaulting to yaml.");
            storageType = "yaml";
        }

        transfersEnabled = c.getBoolean("features.transfers", true);
        leaderboardEnabled = c.getBoolean("features.leaderboard", true);
        leaderboardSize = Math.max(1, c.getInt("features.leaderboard-size", 10));

        minDeposit = Math.max(1, c.getLong("limits.min-deposit", 1));
        minWithdraw = Math.max(1, c.getLong("limits.min-withdraw", 1));
        minTransfer = Math.max(1, c.getLong("limits.min-transfer", 1));

        autosaveEnabled = c.getBoolean("storage.autosave.enabled", true);
        autosaveIntervalSeconds = Math.max(30, c.getInt("storage.autosave.interval-seconds", 300));
        depositKeepXpBar = c.getBoolean("features.keep-xp-bar-on-deposit-all", false);

        // Interest — off by default, fully configurable.
        interestEnabled = c.getBoolean("interest.enabled", false);
        interestRatePercent = Math.max(0.0, c.getDouble("interest.rate-percent", 1.0));
        interestIntervalHours = Math.max(0, c.getInt("interest.interval.hours", 1));
        interestIntervalMinutes = Math.max(0, c.getInt("interest.interval.minutes", 0));
        interestIntervalSeconds = Math.max(0, c.getInt("interest.interval.seconds", 0));
        if (interestIntervalHours == 0 && interestIntervalMinutes == 0 && interestIntervalSeconds == 0) {
            plugin.getLogger().warning("interest.interval is zero; defaulting to 1 hour.");
            interestIntervalHours = 1;
        }
        interestMinBalance = Math.max(0L, c.getLong("interest.min-balance", 1));
        interestMaxPayout = Math.max(0L, c.getLong("interest.max-payout", 0));
        interestOfflinePlayers = c.getBoolean("interest.pay-offline-players", true);
        interestNotify = c.getBoolean("interest.notify-players", true);
        soundInterest = c.getString("sounds.interest", "ENTITY_EXPERIENCE_ORB_PICKUP");

        soundsEnabled = c.getBoolean("sounds.enabled", true);
        soundDeposit = c.getString("sounds.deposit", "ENTITY_EXPERIENCE_ORB_PICKUP");
        soundWithdraw = c.getString("sounds.withdraw", "ENTITY_PLAYER_LEVELUP");
        soundTransfer = c.getString("sounds.transfer", "ENTITY_ARROW_HIT_PLAYER");
        soundError = c.getString("sounds.error", "ENTITY_VILLAGER_NO");
        soundGuiOpen = c.getString("sounds.gui-open", "BLOCK_CHEST_OPEN");
        soundGuiClick = c.getString("sounds.gui-click", "UI_BUTTON_CLICK");

        guiTitle = c.getString("gui.title", "<dark_aqua>XP Bank</dark_aqua>");
        guiRows = Math.min(6, Math.max(1, c.getInt("gui.rows", 3)));
        guiBalanceIcon = material(c.getString("gui.icons.balance", "EXPERIENCE_BOTTLE"), Material.EXPERIENCE_BOTTLE);
        guiDepositIcon = material(c.getString("gui.icons.deposit", "LIME_DYE"), Material.LIME_DYE);
        guiWithdrawIcon = material(c.getString("gui.icons.withdraw", "RED_DYE"), Material.RED_DYE);
        guiFiller = material(c.getString("gui.icons.filler", "GRAY_STAINED_GLASS_PANE"),
                Material.GRAY_STAINED_GLASS_PANE);
    }

    private Material material(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        Material m = Material.matchMaterial(name.toUpperCase(Locale.US));
        if (m == null) {
            plugin.getLogger().warning("Unknown material '" + name + "', using " + fallback + ".");
            return fallback;
        }
        return m;
    }

    /** Resolves a configured sound name to a Bukkit {@link Sound}, or null. */
    public Sound sound(String key) {
        String name = switch (key) {
            case "deposit" -> soundDeposit;
            case "withdraw" -> soundWithdraw;
            case "transfer" -> soundTransfer;
            case "error" -> soundError;
            case "gui-open" -> soundGuiOpen;
            case "gui-click" -> soundGuiClick;
            case "interest" -> soundInterest;
            default -> null;
        };
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Sound.valueOf(name.toUpperCase(Locale.US));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown sound '" + name + "' for '" + key + "'.");
            return null;
        }
    }

    public String storageType() {
        return storageType;
    }

    public boolean transfersEnabled() {
        return transfersEnabled;
    }

    public boolean leaderboardEnabled() {
        return leaderboardEnabled;
    }

    public int leaderboardSize() {
        return leaderboardSize;
    }

    public long minDeposit() {
        return minDeposit;
    }

    public long minWithdraw() {
        return minWithdraw;
    }

    public long minTransfer() {
        return minTransfer;
    }

    public boolean autosaveEnabled() {
        return autosaveEnabled;
    }

    public int autosaveIntervalSeconds() {
        return autosaveIntervalSeconds;
    }

    public boolean depositKeepXpBar() {
        return depositKeepXpBar;
    }

    public boolean soundsEnabled() {
        return soundsEnabled;
    }

    public boolean interestEnabled() {
        return interestEnabled;
    }

    public double interestRatePercent() {
        return interestRatePercent;
    }

    /** Interest interval expressed in server ticks (minimum 1 tick). */
    public long interestIntervalTicks() {
        return Math.max(1L, interestIntervalMillis() / 50L);
    }

    /** Interest interval in milliseconds. */
    public long interestIntervalMillis() {
        long seconds = interestIntervalHours * 3600L
                + interestIntervalMinutes * 60L
                + interestIntervalSeconds;
        return Math.max(1000L, seconds * 1000L);
    }

    /** Human-readable description of the interest interval, e.g. "1h 30m". */
    public String interestIntervalDescription() {
        StringBuilder sb = new StringBuilder();
        if (interestIntervalHours > 0) {
            sb.append(interestIntervalHours).append("h ");
        }
        if (interestIntervalMinutes > 0) {
            sb.append(interestIntervalMinutes).append("m ");
        }
        if (interestIntervalSeconds > 0) {
            sb.append(interestIntervalSeconds).append("s");
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? "1h" : out;
    }

    public long interestMinBalance() {
        return interestMinBalance;
    }

    public long interestMaxPayout() {
        return interestMaxPayout;
    }

    public boolean interestOfflinePlayers() {
        return interestOfflinePlayers;
    }

    public boolean interestNotify() {
        return interestNotify;
    }

    public String guiTitle() {
        return guiTitle;
    }

    public int guiRows() {
        return guiRows;
    }

    public Material guiBalanceIcon() {
        return guiBalanceIcon;
    }

    public Material guiDepositIcon() {
        return guiDepositIcon;
    }

    public Material guiWithdrawIcon() {
        return guiWithdrawIcon;
    }

    public Material guiFiller() {
        return guiFiller;
    }
}
