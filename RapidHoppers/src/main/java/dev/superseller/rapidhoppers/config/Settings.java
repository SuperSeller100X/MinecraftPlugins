package dev.superseller.rapidhoppers.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed, validated view over {@code config.yml}.
 *
 * <p>Every value is clamped into a safe range on load so a malformed config can
 * never take the server down. Runtime changes made through commands or the GUI
 * are written back through the {@code set*} methods and persisted by
 * {@link ConfigService}.</p>
 */
public final class Settings {

    /** Vanilla hoppers move one item every 8 ticks. */
    public static final int VANILLA_INTERVAL_TICKS = 8;

    /**
     * Items moved per transfer. Always one, exactly like vanilla — RapidHoppers
     * changes the hopper <em>rate</em>, never the amount.
     */
    public static final int ITEMS_PER_TRANSFER = 1;

    public static final int MIN_INTERVAL = 1;
    public static final int MAX_INTERVAL = 8;

    /** World handling mode. */
    public enum WorldMode {
        BLACKLIST,
        WHITELIST;

        public static WorldMode parse(String raw, WorldMode fallback) {
            if (raw == null) {
                return fallback;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return fallback;
            }
        }
    }

    /**
     * Accelerated container families.
     *
     * <p>Only containers that vanilla ticks on a hopper clock are listed.
     * Droppers and dispensers fire on a redstone pulse, so there is no rate to
     * accelerate — they are left entirely to vanilla.</p>
     */
    public enum ContainerType {
        HOPPER("hopper", "Hopper"),
        HOPPER_MINECART("hopper-minecart", "Hopper minecart");

        private final String path;
        private final String display;

        ContainerType(String path, String display) {
            this.path = path;
            this.display = display;
        }

        public String path() {
            return path;
        }

        public String display() {
            return display;
        }

        public String configKey() {
            return "containers." + path + ".enabled";
        }

        public static ContainerType byName(String raw) {
            if (raw == null) {
                return null;
            }
            String needle = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
            for (ContainerType type : values()) {
                if (type.path.equals(needle) || type.name().toLowerCase(Locale.ROOT).equals(needle)) {
                    return type;
                }
            }
            return null;
        }
    }

    private boolean enabled = true;
    private int intervalTicks = 2;
    private int scanIntervalTicks = 100;
    private int playerActivityRadiusChunks = 6;

    private final boolean[] containerEnabled = new boolean[ContainerType.values().length];
    private boolean hopperPullFromAbove = true;
    private boolean hopperPushToFacing = true;
    private boolean minecartPullFromAbove = true;

    private WorldMode worldMode = WorldMode.BLACKLIST;
    private final List<String> worldList = new ArrayList<>();

    private boolean throttleEnabled = true;
    private double throttleSoftTps = 18.0D;
    private double throttleHardTps = 14.0D;
    private int throttleMultiplier = 3;
    private int throttleCheckSeconds = 5;
    private boolean throttleLogChanges = true;

    private int maxContainersPerChunk = 96;
    private int maxContainersPerWorld = 20000;
    private int maxTransfersPerTick = 2000;

    private boolean soundsEnabled = true;
    private String guiTitle = "RapidHoppers";
    private int guiRows = 5;
    private String guiFiller = "GRAY_STAINED_GLASS_PANE";
    private int guiRefreshTicks = 20;

    private boolean debug;

    public void load(FileConfiguration cfg) {
        enabled = cfg.getBoolean("enabled", true);

        intervalTicks = clamp(cfg.getInt("engine.interval-ticks", 2), MIN_INTERVAL, MAX_INTERVAL);
        scanIntervalTicks = clamp(cfg.getInt("engine.scan-interval-ticks", 100), 20, 12000);
        playerActivityRadiusChunks = clamp(cfg.getInt("engine.player-activity-radius-chunks", 6), 0, 64);

        containerEnabled[ContainerType.HOPPER.ordinal()] = cfg.getBoolean("containers.hopper.enabled", true);
        containerEnabled[ContainerType.HOPPER_MINECART.ordinal()] =
                cfg.getBoolean("containers.hopper-minecart.enabled", true);

        hopperPullFromAbove = cfg.getBoolean("containers.hopper.pull-from-above", true);
        hopperPushToFacing = cfg.getBoolean("containers.hopper.push-to-facing", true);
        minecartPullFromAbove = cfg.getBoolean("containers.hopper-minecart.pull-from-above", true);

        worldMode = WorldMode.parse(cfg.getString("worlds.mode", "BLACKLIST"), WorldMode.BLACKLIST);
        worldList.clear();
        for (String world : cfg.getStringList("worlds.list")) {
            if (world != null && !world.isBlank()) {
                worldList.add(world);
            }
        }

        throttleEnabled = cfg.getBoolean("performance.throttle.enabled", true);
        throttleSoftTps = clampD(cfg.getDouble("performance.throttle.soft-tps", 18.0D), 1.0D, 20.0D);
        throttleHardTps = clampD(cfg.getDouble("performance.throttle.hard-tps", 14.0D), 1.0D, 20.0D);
        if (throttleHardTps > throttleSoftTps) {
            throttleHardTps = throttleSoftTps;
        }
        throttleMultiplier = clamp(cfg.getInt("performance.throttle.interval-multiplier", 3), 1, 16);
        throttleCheckSeconds = clamp(cfg.getInt("performance.throttle.check-interval-seconds", 5), 1, 300);
        throttleLogChanges = cfg.getBoolean("performance.throttle.log-state-changes", true);

        maxContainersPerChunk = Math.max(0, cfg.getInt("performance.max-containers-per-chunk", 96));
        maxContainersPerWorld = Math.max(0, cfg.getInt("performance.max-containers-per-world", 20000));
        maxTransfersPerTick = Math.max(0, cfg.getInt("performance.max-transfers-per-tick", 2000));

        soundsEnabled = cfg.getBoolean("sounds.enabled", true);
        guiTitle = cfg.getString("gui.title", "RapidHoppers");
        guiRows = clamp(cfg.getInt("gui.rows", 5), 3, 6);
        guiFiller = cfg.getString("gui.filler", "GRAY_STAINED_GLASS_PANE");
        guiRefreshTicks = Math.max(0, cfg.getInt("gui.refresh-ticks", 20));

        debug = cfg.getBoolean("debug", false);
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clampD(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Speed factor compared to vanilla, e.g. interval 2 -> 4.0x. */
    public double speedFactor() {
        return (double) VANILLA_INTERVAL_TICKS / (double) intervalTicks;
    }

    /** Items moved per second by one hopper at the configured interval. */
    public double itemsPerSecond() {
        return 20.0D / (double) intervalTicks;
    }

    /** True when acceleration applies in the given world. */
    public boolean appliesToWorld(String worldName) {
        boolean listed = false;
        for (String entry : worldList) {
            if (entry.equalsIgnoreCase(worldName)) {
                listed = true;
                break;
            }
        }
        return worldMode == WorldMode.WHITELIST ? listed : !listed;
    }

    public boolean isContainerEnabled(ContainerType type) {
        return containerEnabled[type.ordinal()];
    }

    public void setContainerEnabled(ContainerType type, boolean value) {
        containerEnabled[type.ordinal()] = value;
    }

    // --- getters / setters --------------------------------------------------

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getIntervalTicks() {
        return intervalTicks;
    }

    public void setIntervalTicks(int intervalTicks) {
        this.intervalTicks = clamp(intervalTicks, MIN_INTERVAL, MAX_INTERVAL);
    }

    /** Always 1: RapidHoppers changes the rate, not the amount. */
    public int getItemsPerTransfer() {
        return ITEMS_PER_TRANSFER;
    }

    public int getScanIntervalTicks() {
        return scanIntervalTicks;
    }

    public int getPlayerActivityRadiusChunks() {
        return playerActivityRadiusChunks;
    }

    public boolean isHopperPullFromAbove() {
        return hopperPullFromAbove;
    }

    public boolean isHopperPushToFacing() {
        return hopperPushToFacing;
    }

    public boolean isMinecartPullFromAbove() {
        return minecartPullFromAbove;
    }

    public WorldMode getWorldMode() {
        return worldMode;
    }

    public void setWorldMode(WorldMode worldMode) {
        this.worldMode = worldMode;
    }

    public List<String> getWorldList() {
        return worldList;
    }

    public boolean isThrottleEnabled() {
        return throttleEnabled;
    }

    public void setThrottleEnabled(boolean throttleEnabled) {
        this.throttleEnabled = throttleEnabled;
    }

    public double getThrottleSoftTps() {
        return throttleSoftTps;
    }

    public void setThrottleSoftTps(double tps) {
        this.throttleSoftTps = clampD(tps, 1.0D, 20.0D);
        if (throttleHardTps > throttleSoftTps) {
            throttleHardTps = throttleSoftTps;
        }
    }

    public double getThrottleHardTps() {
        return throttleHardTps;
    }

    public void setThrottleHardTps(double tps) {
        this.throttleHardTps = clampD(tps, 1.0D, throttleSoftTps);
    }

    public int getThrottleMultiplier() {
        return throttleMultiplier;
    }

    public int getThrottleCheckSeconds() {
        return throttleCheckSeconds;
    }

    public boolean isThrottleLogChanges() {
        return throttleLogChanges;
    }

    public int getMaxContainersPerChunk() {
        return maxContainersPerChunk;
    }

    public void setMaxContainersPerChunk(int value) {
        this.maxContainersPerChunk = Math.max(0, value);
    }

    public int getMaxContainersPerWorld() {
        return maxContainersPerWorld;
    }

    public int getMaxTransfersPerTick() {
        return maxTransfersPerTick;
    }

    public boolean isSoundsEnabled() {
        return soundsEnabled;
    }

    public String getGuiTitle() {
        return guiTitle;
    }

    public int getGuiRows() {
        return guiRows;
    }

    public String getGuiFiller() {
        return guiFiller;
    }

    public int getGuiRefreshTicks() {
        return guiRefreshTicks;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }
}
