package dev.superseller.combattag.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Typed, validated access to {@code config.yml}. Every value has a safe fallback so a
 * malformed configuration can never prevent the plugin from enabling.
 */
public final class PluginConfig {

    /** A configurable sound effect (sound key, volume, pitch). */
    public record SoundEffect(Sound sound, float volume, float pitch) { }

    private final JavaPlugin plugin;

    private int tagSeconds;
    private int projectileTagSeconds;
    private boolean tagVictim;
    private boolean tagAttacker;
    private boolean tagOnProjectile;
    private boolean tagOnSplashPotion;
    private boolean tagOnPetDamage;
    private boolean cancelTagOnDeath;
    private boolean refreshOnHit;
    private List<String> disabledWorlds;

    private boolean blockShops;
    private Set<String> shopCommands;
    private Set<String> shopInventoryTitles;
    private Set<String> shopHolderClasses;

    private boolean blockTeleports;
    private Set<String> teleportCommands;
    private Set<String> allowedTeleportCauses;
    private boolean blockEnderPearls;
    private boolean blockChorusFruit;

    private boolean blockEasyMending;
    private Set<String> easyMendingCommands;
    private Set<String> easyMendingHolderClasses;

    private boolean blockedCommandsEnabled;
    private Set<String> blockedCommands;
    private boolean blockedCommandsWhitelistMode;
    private Set<String> allowedCommands;

    private boolean punishCombatLog;
    private boolean combatLogKill;
    private boolean combatLogDropInventory;
    private boolean combatLogBroadcast;
    private boolean combatLogLogToFile;
    private List<String> combatLogCommands;

    private boolean bossBarEnabled;
    private String bossBarColor;
    private String bossBarOverlay;
    private boolean actionBarEnabled;
    private boolean titleOnTagEnabled;
    private boolean soundsEnabled;

    private SoundEffect soundTagStart;
    private SoundEffect soundTagEnd;
    private SoundEffect soundBlocked;
    private SoundEffect soundGuiOpen;
    private SoundEffect soundGuiClick;
    private SoundEffect soundCombatLog;

    private boolean guiEnabled;
    private int guiRows;

    private boolean placeholderApiEnabled;
    private boolean statsEnabled;
    private boolean updateNotify;
    private int displayIntervalTicks;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Loads (or reloads) config.yml from disk. */
    public void load() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        c.options().copyDefaults(true);

        tagSeconds = clamp(c.getInt("combat.tag-seconds", 10), 1, 3600);
        projectileTagSeconds = clamp(c.getInt("combat.projectile-tag-seconds", tagSeconds), 1, 3600);
        tagVictim = c.getBoolean("combat.tag-victim", true);
        tagAttacker = c.getBoolean("combat.tag-attacker", true);
        tagOnProjectile = c.getBoolean("combat.tag-on-projectile", true);
        tagOnSplashPotion = c.getBoolean("combat.tag-on-splash-potion", true);
        tagOnPetDamage = c.getBoolean("combat.tag-on-pet-damage", false);
        cancelTagOnDeath = c.getBoolean("combat.clear-tag-on-death", true);
        refreshOnHit = c.getBoolean("combat.refresh-on-hit", true);
        disabledWorlds = lower(c.getStringList("combat.disabled-worlds"));

        blockShops = c.getBoolean("restrictions.shops.enabled", true);
        shopCommands = commandSet(c.getStringList("restrictions.shops.commands"));
        shopInventoryTitles = lowerSet(c.getStringList("restrictions.shops.inventory-titles"));
        shopHolderClasses = lowerSet(c.getStringList("restrictions.shops.inventory-holder-classes"));

        blockTeleports = c.getBoolean("restrictions.teleports.enabled", true);
        teleportCommands = commandSet(c.getStringList("restrictions.teleports.commands"));
        allowedTeleportCauses = upperSet(c.getStringList("restrictions.teleports.allowed-causes"));
        blockEnderPearls = c.getBoolean("restrictions.teleports.block-ender-pearls", true);
        blockChorusFruit = c.getBoolean("restrictions.teleports.block-chorus-fruit", true);

        blockEasyMending = c.getBoolean("restrictions.easymending.enabled", true);
        easyMendingCommands = commandSet(c.getStringList("restrictions.easymending.commands"));
        easyMendingHolderClasses = lowerSet(c.getStringList("restrictions.easymending.inventory-holder-classes"));

        blockedCommandsEnabled = c.getBoolean("restrictions.commands.enabled", true);
        blockedCommands = commandSet(c.getStringList("restrictions.commands.blacklist"));
        blockedCommandsWhitelistMode = c.getBoolean("restrictions.commands.whitelist-mode", false);
        allowedCommands = commandSet(c.getStringList("restrictions.commands.whitelist"));

        punishCombatLog = c.getBoolean("combat-logging.enabled", true);
        combatLogKill = c.getBoolean("combat-logging.kill-player", true);
        combatLogDropInventory = c.getBoolean("combat-logging.drop-inventory", true);
        combatLogBroadcast = c.getBoolean("combat-logging.broadcast", true);
        combatLogLogToFile = c.getBoolean("combat-logging.log-to-console", true);
        combatLogCommands = c.getStringList("combat-logging.run-commands");

        bossBarEnabled = c.getBoolean("display.boss-bar.enabled", true);
        bossBarColor = c.getString("display.boss-bar.color", "RED");
        bossBarOverlay = c.getString("display.boss-bar.overlay", "PROGRESS");
        actionBarEnabled = c.getBoolean("display.action-bar.enabled", true);
        titleOnTagEnabled = c.getBoolean("display.title-on-tag", false);
        displayIntervalTicks = clamp(c.getInt("display.update-interval-ticks", 10), 1, 100);

        soundsEnabled = c.getBoolean("sounds.enabled", true);
        soundTagStart = sound(c, "sounds.tag-start", "ENTITY_ENDER_DRAGON_GROWL", 0.6f, 1.4f);
        soundTagEnd = sound(c, "sounds.tag-end", "ENTITY_PLAYER_LEVELUP", 0.7f, 1.6f);
        soundBlocked = sound(c, "sounds.action-blocked", "BLOCK_NOTE_BLOCK_BASS", 1.0f, 0.6f);
        soundGuiOpen = sound(c, "sounds.gui-open", "BLOCK_CHEST_OPEN", 0.7f, 1.2f);
        soundGuiClick = sound(c, "sounds.gui-click", "UI_BUTTON_CLICK", 0.7f, 1.0f);
        soundCombatLog = sound(c, "sounds.combat-log", "ENTITY_WITHER_SPAWN", 0.8f, 1.0f);

        guiEnabled = c.getBoolean("gui.enabled", true);
        guiRows = clamp(c.getInt("gui.rows", 3), 1, 6);

        placeholderApiEnabled = c.getBoolean("integrations.placeholderapi", true);
        statsEnabled = c.getBoolean("integrations.statistics", true);
        updateNotify = c.getBoolean("integrations.notify-admins-on-block", false);

        plugin.saveConfig();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static List<String> lower(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) {
            if (s != null) {
                out.add(s.toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static Set<String> lowerSet(List<String> in) {
        Set<String> out = new LinkedHashSet<>(lower(in));
        return Collections.unmodifiableSet(out);
    }

    private static Set<String> upperSet(List<String> in) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : in) {
            if (s != null) {
                out.add(s.toUpperCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /** Normalises command entries: strips a leading slash and lowercases. */
    public static String normalizeCommand(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        return s;
    }

    private static Set<String> commandSet(List<String> in) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : in) {
            String n = normalizeCommand(s);
            if (!n.isEmpty()) {
                out.add(n);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    private SoundEffect sound(FileConfiguration c, String path, String def, float vol, float pitch) {
        ConfigurationSection section = c.getConfigurationSection(path);
        String key = section != null ? section.getString("sound", def) : def;
        float volume = section != null ? (float) section.getDouble("volume", vol) : vol;
        float p = section != null ? (float) section.getDouble("pitch", pitch) : pitch;
        return new SoundEffect(resolveSound(key, def), volume, p);
    }

    /**
     * Resolves a sound name against the modern {@link Registry} (Minecraft 26.2 no longer
     * guarantees {@code Sound} to be an enum), falling back to the default key.
     */
    public static Sound resolveSound(String key, String fallback) {
        Sound s = lookupSound(key);
        if (s == null) {
            s = lookupSound(fallback);
        }
        return s;
    }

    private static Sound lookupSound(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        // Modern registry lookup: "block.anvil.use" or "minecraft:block.anvil.use"
        String namespaced = trimmed.toLowerCase(Locale.ROOT).replace('_', '.');
        if (!namespaced.contains(":")) {
            namespaced = "minecraft:" + namespaced;
        }
        try {
            org.bukkit.NamespacedKey nk = org.bukkit.NamespacedKey.fromString(namespaced);
            if (nk != null) {
                Sound found = Registry.SOUNDS.get(nk);
                if (found != null) {
                    return found;
                }
            }
        } catch (Throwable ignored) {
            // Registry unavailable — fall through to the constant lookup
        }
        // Legacy constant lookup: "BLOCK_ANVIL_USE"
        try {
            java.lang.reflect.Field field = Sound.class.getField(trimmed.toUpperCase(Locale.ROOT));
            Object value = field.get(null);
            if (value instanceof Sound sound) {
                return sound;
            }
        } catch (Throwable ignored) {
            // Unknown sound
        }
        return null;
    }

    public int getTagSeconds() { return tagSeconds; }
    public int getProjectileTagSeconds() { return projectileTagSeconds; }
    public boolean isTagVictim() { return tagVictim; }
    public boolean isTagAttacker() { return tagAttacker; }
    public boolean isTagOnProjectile() { return tagOnProjectile; }
    public boolean isTagOnSplashPotion() { return tagOnSplashPotion; }
    public boolean isTagOnPetDamage() { return tagOnPetDamage; }
    public boolean isClearTagOnDeath() { return cancelTagOnDeath; }
    public boolean isRefreshOnHit() { return refreshOnHit; }
    public boolean isWorldDisabled(String world) {
        return world != null && disabledWorlds.contains(world.toLowerCase(Locale.ROOT));
    }
    public List<String> getDisabledWorlds() { return disabledWorlds; }

    public boolean isBlockShops() { return blockShops; }
    public Set<String> getShopCommands() { return shopCommands; }
    public Set<String> getShopInventoryTitles() { return shopInventoryTitles; }
    public Set<String> getShopHolderClasses() { return shopHolderClasses; }

    public boolean isBlockTeleports() { return blockTeleports; }
    public Set<String> getTeleportCommands() { return teleportCommands; }
    public Set<String> getAllowedTeleportCauses() { return allowedTeleportCauses; }
    public boolean isBlockEnderPearls() { return blockEnderPearls; }
    public boolean isBlockChorusFruit() { return blockChorusFruit; }

    public boolean isBlockEasyMending() { return blockEasyMending; }
    public Set<String> getEasyMendingCommands() { return easyMendingCommands; }
    public Set<String> getEasyMendingHolderClasses() { return easyMendingHolderClasses; }

    public boolean isBlockedCommandsEnabled() { return blockedCommandsEnabled; }
    public Set<String> getBlockedCommands() { return blockedCommands; }
    public boolean isCommandWhitelistMode() { return blockedCommandsWhitelistMode; }
    public Set<String> getAllowedCommands() { return allowedCommands; }

    public boolean isPunishCombatLog() { return punishCombatLog; }
    public boolean isCombatLogKill() { return combatLogKill; }
    public boolean isCombatLogDropInventory() { return combatLogDropInventory; }
    public boolean isCombatLogBroadcast() { return combatLogBroadcast; }
    public boolean isCombatLogLogToConsole() { return combatLogLogToFile; }
    public List<String> getCombatLogCommands() { return combatLogCommands; }

    public boolean isBossBarEnabled() { return bossBarEnabled; }
    public String getBossBarColor() { return bossBarColor; }
    public String getBossBarOverlay() { return bossBarOverlay; }
    public boolean isActionBarEnabled() { return actionBarEnabled; }
    public boolean isTitleOnTagEnabled() { return titleOnTagEnabled; }
    public int getDisplayIntervalTicks() { return displayIntervalTicks; }

    public boolean isSoundsEnabled() { return soundsEnabled; }
    public SoundEffect getSoundTagStart() { return soundTagStart; }
    public SoundEffect getSoundTagEnd() { return soundTagEnd; }
    public SoundEffect getSoundBlocked() { return soundBlocked; }
    public SoundEffect getSoundGuiOpen() { return soundGuiOpen; }
    public SoundEffect getSoundGuiClick() { return soundGuiClick; }
    public SoundEffect getSoundCombatLog() { return soundCombatLog; }

    public boolean isGuiEnabled() { return guiEnabled; }
    public int getGuiRows() { return guiRows; }

    public boolean isPlaceholderApiEnabled() { return placeholderApiEnabled; }
    public boolean isStatsEnabled() { return statsEnabled; }
    public boolean isNotifyAdminsOnBlock() { return updateNotify; }

    /** Live-updates the combat duration and persists it. */
    public void setTagSeconds(int seconds) {
        this.tagSeconds = clamp(seconds, 1, 3600);
        plugin.getConfig().set("combat.tag-seconds", this.tagSeconds);
        plugin.saveConfig();
    }
}
