package dev.superseller.playervault.config;

import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.superseller.playervault.gui.GuiLayout;
import dev.superseller.playervault.pricing.PriceCalculator;
import dev.superseller.playervault.util.Numbers;

/**
 * Immutable snapshot of {@code config.yml}.
 *
 * <p>Reloading swaps the whole object instead of mutating it, so code holding a
 * reference to the old settings keeps working for the duration of a tick and no
 * reader ever observes a half-updated configuration.
 */
public record Settings(
        Storage storage,
        Vault vault,
        Upgrade upgrade,
        Economy economy,
        Gui gui,
        boolean placeholders,
        boolean debug) {

    /** Where vault contents are persisted. */
    public enum Backend {
        YAML, SQLITE
    }

    /** How {@code playervault.rows.<n>} permissions combine with the starting size. */
    public enum BonusMode {
        HIGHEST, ADD, OFF
    }

    public record Storage(Backend backend, int autoSaveSeconds, boolean saveOnQuit, String journalMode,
                          int busyTimeoutMs) {
    }

    public record Vault(int startingRows, int maxRows, BonusMode bonusMode) {

        /** {@code true} when purchases are capped by {@link #maxRows()}. */
        public boolean limited() {
            return maxRows > 0;
        }
    }

    public record Upgrade(double basePrice, double multiplier, int roundDecimals, int maxRowsPerPurchase,
                          int confirmThreshold) {
    }

    public record Economy(boolean enabled, double refundPercent, boolean logTransactions) {
    }

    public record ButtonSpec(Material material, int slot) {
    }

    public record Buttons(ButtonSpec upgrade, ButtonSpec sort, ButtonSpec deposit, ButtonSpec withdraw,
                          ButtonSpec info, ButtonSpec page, ButtonSpec previous, ButtonSpec next,
                          ButtonSpec filler) {
    }

    public record Gui(String title, int pageRows, boolean rememberPage, boolean sounds, String sound,
                      Buttons buttons) {
    }

    /** The row ladder derived from the {@code upgrade} section. */
    public PriceCalculator pricing() {
        return new PriceCalculator(upgrade.basePrice(), upgrade.multiplier(), upgrade.roundDecimals());
    }

    /** Reads and validates the plugin's {@code config.yml}. */
    public static Settings load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();

        Storage storage = new Storage(
                backend(config.getString("storage.type", "YAML")),
                Math.max(0, config.getInt("storage.auto-save-seconds", 300)),
                config.getBoolean("storage.save-on-quit", true),
                config.getString("storage.sqlite.journal-mode", "WAL"),
                Math.clamp(config.getInt("storage.sqlite.busy-timeout-ms", 5000), 0, 60_000));

        Vault vault = new Vault(
                Math.max(1, config.getInt("vault.starting-rows", 3)),
                Math.max(-1, config.getInt("vault.max-rows", -1)),
                bonusMode(config.getString("vault.bonus-rows", "HIGHEST")));

        Upgrade upgrade = new Upgrade(
                Numbers.amount(config.getString("upgrade.base-price", "10k"), 10_000.0d),
                Math.max(0.0d, Numbers.amount(config.getString("upgrade.multiplier", "1.5"), 1.5d)),
                Math.clamp(config.getInt("upgrade.round-decimals", 2), 0, 8),
                Math.clamp(config.getInt("upgrade.max-rows-per-purchase", 25), 1, 1024),
                Math.max(0, config.getInt("upgrade.confirm-threshold", 5)));

        Economy economy = new Economy(
                config.getBoolean("economy.enabled", true),
                Math.clamp(Numbers.amount(config.getString("economy.refund-percent", "0"), 0.0d), 0.0d, 100.0d),
                config.getBoolean("economy.log-transactions", true));

        Gui gui = new Gui(
                config.getString("gui.title", "<dark_aqua><bold>PlayerVault</bold> <dark_gray>» <gray>Page <white>%page%<gray>/<white>%pages%"),
                Math.clamp(config.getInt("gui.page-rows", GuiLayout.MAX_STORAGE_ROWS_PER_PAGE),
                        1, GuiLayout.MAX_STORAGE_ROWS_PER_PAGE),
                config.getBoolean("gui.remember-page", true),
                config.getBoolean("gui.sounds", true),
                config.getString("gui.sound", "ui.button.click"),
                buttons(config));

        return new Settings(storage, vault, upgrade, economy, gui,
                config.getBoolean("placeholders.enabled", true),
                config.getBoolean("debug", false));
    }

    private static Backend backend(String raw) {
        if (raw != null && raw.trim().toUpperCase(Locale.ROOT).startsWith("SQL")) {
            return Backend.SQLITE;
        }
        return Backend.YAML;
    }

    private static BonusMode bonusMode(String raw) {
        if (raw == null) {
            return BonusMode.HIGHEST;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "ADD", "ADDITIVE", "SUM" -> BonusMode.ADD;
            case "OFF", "NONE", "FALSE", "DISABLED" -> BonusMode.OFF;
            default -> BonusMode.HIGHEST;
        };
    }

    private static Buttons buttons(FileConfiguration config) {
        return new Buttons(
                button(config, "gui.buttons.upgrade", Material.NETHER_STAR, 7),
                button(config, "gui.buttons.sort", Material.STRUCTURE_VOID, 1),
                button(config, "gui.buttons.deposit-all", Material.HOPPER, 2),
                button(config, "gui.buttons.withdraw-all", Material.DROPPER, 5),
                button(config, "gui.buttons.info", Material.BOOK, 3),
                button(config, "gui.buttons.page-indicator", Material.PAPER, 4),
                button(config, "gui.buttons.previous-page", Material.ARROW, 0),
                button(config, "gui.buttons.next-page", Material.ARROW, 8),
                button(config, "gui.buttons.filler", Material.GRAY_STAINED_GLASS_PANE, 6));
    }

    private static ButtonSpec button(FileConfiguration config, String path, Material fallbackMaterial,
                                     int fallbackSlot) {
        Material material = Material.matchMaterial(config.getString(path + ".material", ""));
        if (material == null || !material.isItem()) {
            material = fallbackMaterial;
        }
        int slot = Math.clamp(config.getInt(path + ".slot", fallbackSlot), 0, GuiLayout.SLOTS_PER_ROW - 1);
        return new ButtonSpec(material, slot);
    }
}
