package dev.superseller.homesgui.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

/** Immutable, validated snapshot of config.yml. Replaced atomically on reload. */
public record HomesSettings(Homes homes, Pricing pricing, Economy economy, Storage storage, Gui gui,
                            SoundSettings sounds) {

    public static final int ABSOLUTE_MAX_HOMES = 1_000_000;

    public static HomesSettings load(FileConfiguration config, Logger logger) {
        int freeSlots = intInRange(config, "homes.free-slots", 4, 0, ABSOLUTE_MAX_HOMES, logger);
        int maxTotal = config.getInt("homes.max-total", -1);
        if (maxTotal < -1) {
            logger.warning("homes.max-total must be -1 or non-negative; using -1.");
            maxTotal = -1;
        }
        maxTotal = Math.min(maxTotal, ABSOLUTE_MAX_HOMES);
        int maxBulk = intInRange(config, "homes.max-bulk-purchase", 25, 1, 1000, logger);
        String permissionPrefix = config.getString("homes.permission-slot-prefix", "homesgui.slots.");
        if (permissionPrefix == null || permissionPrefix.isBlank()) {
            permissionPrefix = "homesgui.slots.";
        }
        Homes homes = new Homes(freeSlots, maxTotal, maxBulk, permissionPrefix,
                config.getBoolean("homes.allow-setting", true),
                config.getBoolean("homes.allow-deleting", true),
                config.getBoolean("homes.allow-purchases", true));

        double firstPrice = nonNegativeOr(config.getDouble("homes.pricing.first-extra-slot", 1000.0), 1000.0, logger,
                "homes.pricing.first-extra-slot");
        double multiplier = positiveOr(config.getDouble("homes.pricing.multiplier", 1.25), 1.25, logger,
                "homes.pricing.multiplier");
        int decimals = intInRange(config, "homes.pricing.decimal-places", 2, 0, 6, logger);
        String currencySymbol = config.getString("homes.pricing.currency-symbol", "$");
        Pricing pricing = new Pricing(firstPrice, multiplier, decimals,
                currencySymbol == null ? "" : currencySymbol);

        Economy economy = new Economy(config.getBoolean("homes.economy.enabled", true),
                config.getBoolean("homes.economy.free-if-unavailable", false));
        int autosaveSeconds = intInRange(config, "storage.autosave-seconds", 300, 0, 86_400, logger);
        Storage storage = new Storage(autosaveSeconds, config.getBoolean("storage.save-on-quit", true));

        int rows = intInRange(config, "gui.rows", 6, 2, 6, logger);
        int perPage = intInRange(config, "gui.homes-per-page", 45, 1, 45, logger);
        String guiTitle = config.getString("gui.title", "&8&lYour Homes");
        if (guiTitle == null || guiTitle.isBlank()) {
            guiTitle = "&8&lYour Homes";
        }
        Gui gui = new Gui(config.getBoolean("gui.enabled", true),
                guiTitle, rows,
                Math.min(perPage, (rows - 1) * 9), config.getBoolean("gui.fill-empty", true),
                icon(config, "gui.filler", Material.BLACK_STAINED_GLASS_PANE, " ", logger),
                icon(config, "gui.home", Material.LODESTONE, "&6Home #%slot%", logger),
                icon(config, "gui.empty", Material.MAP, "&eSet home #%slot%", logger),
                button(config, "gui.controls.previous", "gui.controls.previous-slot", 0,
                        Material.ARROW, "&ePrevious page", logger),
                button(config, "gui.controls.info", "gui.controls.info-slot", 3,
                        Material.BOOK, "&bHome information", logger),
                button(config, "gui.controls.buy", "gui.controls.buy-slot", 4,
                        Material.EMERALD, "&aBuy a home slot", logger),
                button(config, "gui.controls.close", "gui.controls.close-slot", 5,
                        Material.BARRIER, "&cClose", logger),
                button(config, "gui.controls.next", "gui.controls.next-slot", 8,
                        Material.ARROW, "&eNext page", logger));

        boolean soundEnabled = config.getBoolean("sounds.enabled", true);
        float volume = floatInRange(config, "sounds.volume", 0.8f, 0.0f, 4.0f, logger);
        float pitch = floatInRange(config, "sounds.pitch", 1.0f, 0.0f, 2.0f, logger);
        Map<String, Sound> soundMap = new LinkedHashMap<>();
        for (String key : List.of("gui-open", "gui-click", "teleport", "set", "delete", "purchase", "failure")) {
            String raw = config.getString("sounds." + key, "");
            if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("none")) {
                continue;
            }
            try {
                soundMap.put(key, Sound.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                logger.warning("Unknown sound '" + raw + "' at sounds." + key + "; that sound is disabled.");
            }
        }
        return new HomesSettings(homes, pricing, economy, storage, gui,
                new SoundSettings(soundEnabled, volume, pitch, Map.copyOf(soundMap)));
    }

    private static Icon icon(FileConfiguration config, String path, Material fallback, String fallbackName,
                             Logger logger) {
        String rawMaterial = config.getString(path + ".material", fallback.name());
        Material material = Material.matchMaterial(rawMaterial == null ? "" : rawMaterial);
        if (material == null || material.isAir()) {
            logger.warning("Invalid GUI material at " + path + ".material; using " + fallback.name() + ".");
            material = fallback;
        }
        String name = config.getString(path + ".name", fallbackName);
        List<String> lore = List.copyOf(config.getStringList(path + ".lore"));
        return new Icon(material, name == null ? fallbackName : name, lore);
    }

    private static Button button(FileConfiguration config, String iconPath, String slotPath, int defaultSlot,
                                 Material fallback, String fallbackName, Logger logger) {
        int position = intInRange(config, slotPath, defaultSlot, 0, 8, logger);
        return new Button(position, icon(config, iconPath, fallback, fallbackName, logger));
    }

    private static int intInRange(FileConfiguration config, String path, int fallback, int minimum, int maximum,
                                  Logger logger) {
        int value = config.getInt(path, fallback);
        int clamped = Math.clamp(value, minimum, maximum);
        if (value != clamped) {
            logger.warning(path + " must be between " + minimum + " and " + maximum + "; using " + clamped + ".");
        }
        return clamped;
    }

    private static double nonNegativeOr(double value, double fallback, Logger logger, String path) {
        if (Double.isFinite(value) && value >= 0.0) {
            return value;
        }
        logger.warning(path + " must be a finite non-negative number; using " + fallback + ".");
        return fallback;
    }

    private static double positiveOr(double value, double fallback, Logger logger, String path) {
        if (Double.isFinite(value) && value > 0.0) {
            return value;
        }
        logger.warning(path + " must be a finite positive number; using " + fallback + ".");
        return fallback;
    }

    private static float floatInRange(FileConfiguration config, String path, float fallback, float minimum,
                                      float maximum, Logger logger) {
        double value = config.getDouble(path, fallback);
        if (!Double.isFinite(value)) {
            logger.warning(path + " must be finite; using " + fallback + ".");
            return fallback;
        }
        float clamped = (float) Math.clamp(value, minimum, maximum);
        if (value < minimum || value > maximum) {
            logger.warning(path + " must be between " + minimum + " and " + maximum + "; using " + clamped + ".");
        }
        return clamped;
    }

    public record Homes(int freeSlots, int maxTotal, int maxBulkPurchase, String permissionSlotPrefix,
                        boolean allowSetting, boolean allowDeleting, boolean allowPurchases) { }

    public record Pricing(double firstExtraSlot, double multiplier, int decimalPlaces, String currencySymbol) { }

    public record Economy(boolean enabled, boolean freeIfUnavailable) { }

    public record Storage(int autosaveSeconds, boolean saveOnQuit) { }

    public record Icon(Material material, String name, List<String> lore) {
        public Icon {
            lore = List.copyOf(lore);
        }
    }

    public record Button(int position, Icon icon) { }

    public record Gui(boolean enabled, String title, int rows, int homesPerPage, boolean fillEmpty,
                      Icon filler, Icon home, Icon empty, Button previous, Button info, Button buy,
                      Button close, Button next) {
        public int inventorySize() {
            return rows * 9;
        }

        public int contentSlots() {
            return (rows - 1) * 9;
        }

        public int pageSize() {
            return Math.min(contentSlots(), homesPerPage);
        }
    }

    public record SoundSettings(boolean enabled, float volume, float pitch, Map<String, Sound> sounds) { }
}
