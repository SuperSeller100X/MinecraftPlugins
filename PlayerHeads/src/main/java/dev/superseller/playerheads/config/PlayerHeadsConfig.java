package dev.superseller.playerheads.config;

import dev.superseller.playerheads.PlayerHeadsPlugin;
import dev.superseller.playerheads.util.Names;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed access to config.yml with sane defaults for every setting.
 */
public final class PlayerHeadsConfig {

    /** Hard ceiling applied to every configurable amount, bypass included. */
    private static final int HARD_LIMIT = 65_536;

    private final PlayerHeadsPlugin plugin;

    private int defaultAmount = 1;
    private int maxAmount = 64;
    private boolean dropWhenFull = true;
    private Pattern namePattern = Names.DEFAULT_PATTERN;

    public PlayerHeadsConfig(PlayerHeadsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration yaml = plugin.getConfig();

        defaultAmount = clamp(yaml.getInt("settings.default-amount", 1), 1, HARD_LIMIT);
        maxAmount = clamp(yaml.getInt("settings.max-amount", 64), 1, HARD_LIMIT);
        if (maxAmount < defaultAmount) {
            plugin.getLogger().warning("settings.max-amount (" + maxAmount
                    + ") is smaller than settings.default-amount (" + defaultAmount
                    + ") — clamping max-amount to " + defaultAmount + ".");
            maxAmount = defaultAmount;
        }
        dropWhenFull = yaml.getBoolean("settings.drop-when-full", true);

        String raw = yaml.getString("settings.name-pattern", Names.DEFAULT_PATTERN_STRING);
        try {
            namePattern = Pattern.compile(raw);
        } catch (PatternSyntaxException e) {
            plugin.getLogger().warning("Invalid settings.name-pattern '" + raw + "' (" + e.getMessage()
                    + ") — using the default pattern instead.");
            namePattern = Names.DEFAULT_PATTERN;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Amount used when the command omits the amount argument. */
    public int defaultAmount() {
        return defaultAmount;
    }

    /** Maximum amount a single command may give (playerheads.bypass-max ignores this). */
    public int maxAmount() {
        return maxAmount;
    }

    /** Whether surplus heads are dropped on the ground when the inventory is full. */
    public boolean dropWhenFull() {
        return dropWhenFull;
    }

    /** Pattern a name must match before a skin lookup is attempted. */
    public Pattern namePattern() {
        return namePattern;
    }

    /** Hard ceiling that even playerheads.bypass-max cannot exceed. */
    public static int hardLimit() {
        return HARD_LIMIT;
    }
}
