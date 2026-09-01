package dev.superseller.easymending.config;

import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles loading, caching, and safe access to EasyMending configuration settings.
 */
public final class PluginConfig {

    private final JavaPlugin plugin;
    private final Logger logger;

    private double durabilityPerXp = 2.0;
    private boolean requireMending = true;
    private double nonMendingMultiplier = 1.5;
    private boolean allowPartialRepair = true;
    private int minXpPerRepair = 1;
    private int cooldownSeconds = 0;
    private boolean sneakClickToRepair = false;

    // Bypass options (strictly false by default)
    private boolean bypassEnabled = false;
    private boolean opBypassesCost = false;
    private boolean opBypassesMending = false;
    private boolean opBypassesCooldown = false;

    // Sounds
    private boolean soundsEnabled = true;
    private Sound soundRepairSuccess = Sound.BLOCK_ANVIL_USE;
    private float volRepairSuccess = 1.0f;
    private float pitchRepairSuccess = 1.2f;

    private Sound soundRepairAll = Sound.UI_TOAST_CHALLENGE_COMPLETE;
    private float volRepairAll = 0.8f;
    private float pitchRepairAll = 1.0f;

    private Sound soundNoDamage = Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO;
    private float volNoDamage = 0.8f;
    private float pitchNoDamage = 0.8f;

    private Sound soundNoMending = Sound.ENTITY_VILLAGER_NO;
    private float volNoMending = 0.9f;
    private float pitchNoMending = 0.9f;

    private Sound soundInsufficientXp = Sound.BLOCK_NOTE_BLOCK_BASS;
    private float volInsufficientXp = 1.0f;
    private float pitchInsufficientXp = 0.6f;

    private Sound soundGuiClick = Sound.UI_BUTTON_CLICK;
    private float volGuiClick = 0.7f;
    private float pitchGuiClick = 1.2f;

    private Sound soundGuiOpen = Sound.BLOCK_CHEST_OPEN;
    private float volGuiOpen = 0.7f;
    private float pitchGuiOpen = 1.0f;

    // Particles
    private boolean particlesEnabled = true;
    private Particle particleType = Particle.HAPPY_VILLAGER;
    private int particleCount = 15;

    // GUI
    private String guiTitle = "<gradient:#4facfe:#00f2fe><b>EasyMending</b></gradient> <dark_gray>»</dark_gray> <gray>Repair Station</gray>";
    private boolean guiFillEmptySlots = true;
    private Material guiFillMaterial = Material.GRAY_STAINED_GLASS_PANE;
    private Material guiBorderMaterial = Material.CYAN_STAINED_GLASS_PANE;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void load() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        this.durabilityPerXp = Math.max(0.1, cfg.getDouble("repair.durability-per-xp", 2.0));
        this.requireMending = cfg.getBoolean("repair.require-mending", true);
        this.nonMendingMultiplier = Math.max(0.1, cfg.getDouble("repair.non-mending-cost-multiplier", 1.5));
        this.allowPartialRepair = cfg.getBoolean("repair.allow-partial-repair", true);
        this.minXpPerRepair = Math.max(1, cfg.getInt("repair.min-xp-per-repair", 1));
        this.cooldownSeconds = Math.max(0, cfg.getInt("repair.cooldown-seconds", 0));
        this.sneakClickToRepair = cfg.getBoolean("repair.sneak-click-to-repair", false);

        // Bypass settings (off by default)
        this.bypassEnabled = cfg.getBoolean("bypass.enabled", false);
        this.opBypassesCost = cfg.getBoolean("bypass.op-bypasses-cost", false);
        this.opBypassesMending = cfg.getBoolean("bypass.op-bypasses-mending", false);
        this.opBypassesCooldown = cfg.getBoolean("bypass.op-bypasses-cooldown", false);

        // Sounds
        this.soundsEnabled = cfg.getBoolean("sounds.enabled", true);
        this.soundRepairSuccess = parseSound(cfg.getString("sounds.repair-success.sound"), Sound.BLOCK_ANVIL_USE);
        this.volRepairSuccess = (float) cfg.getDouble("sounds.repair-success.volume", 1.0);
        this.pitchRepairSuccess = (float) cfg.getDouble("sounds.repair-success.pitch", 1.2);

        this.soundRepairAll = parseSound(cfg.getString("sounds.repair-all.sound"), Sound.UI_TOAST_CHALLENGE_COMPLETE);
        this.volRepairAll = (float) cfg.getDouble("sounds.repair-all.volume", 0.8);
        this.pitchRepairAll = (float) cfg.getDouble("sounds.repair-all.pitch", 1.0);

        this.soundNoDamage = parseSound(cfg.getString("sounds.no-damage.sound"), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO);
        this.volNoDamage = (float) cfg.getDouble("sounds.no-damage.volume", 0.8);
        this.pitchNoDamage = (float) cfg.getDouble("sounds.no-damage.pitch", 0.8);

        this.soundNoMending = parseSound(cfg.getString("sounds.no-mending.sound"), Sound.ENTITY_VILLAGER_NO);
        this.volNoMending = (float) cfg.getDouble("sounds.no-mending.volume", 0.9);
        this.pitchNoMending = (float) cfg.getDouble("sounds.no-mending.pitch", 0.9);

        this.soundInsufficientXp = parseSound(cfg.getString("sounds.insufficient-xp.sound"), Sound.BLOCK_NOTE_BLOCK_BASS);
        this.volInsufficientXp = (float) cfg.getDouble("sounds.insufficient-xp.volume", 1.0);
        this.pitchInsufficientXp = (float) cfg.getDouble("sounds.insufficient-xp.pitch", 0.6);

        this.soundGuiClick = parseSound(cfg.getString("sounds.gui-click.sound"), Sound.UI_BUTTON_CLICK);
        this.volGuiClick = (float) cfg.getDouble("sounds.gui-click.volume", 0.7);
        this.pitchGuiClick = (float) cfg.getDouble("sounds.gui-click.pitch", 1.2);

        this.soundGuiOpen = parseSound(cfg.getString("sounds.gui-open.sound"), Sound.BLOCK_CHEST_OPEN);
        this.volGuiOpen = (float) cfg.getDouble("sounds.gui-open.volume", 0.7);
        this.pitchGuiOpen = (float) cfg.getDouble("sounds.gui-open.pitch", 1.0);

        // Particles
        this.particlesEnabled = cfg.getBoolean("particles.enabled", true);
        this.particleType = parseParticle(cfg.getString("particles.type"), Particle.HAPPY_VILLAGER);
        this.particleCount = Math.max(1, cfg.getInt("particles.count", 15));

        // GUI
        this.guiTitle = cfg.getString("gui.title", "<gradient:#4facfe:#00f2fe><b>EasyMending</b></gradient> <dark_gray>»</dark_gray> <gray>Repair Station</gray>");
        this.guiFillEmptySlots = cfg.getBoolean("gui.fill-empty-slots", true);
        this.guiFillMaterial = parseMaterial(cfg.getString("gui.fill-material"), Material.GRAY_STAINED_GLASS_PANE);
        this.guiBorderMaterial = parseMaterial(cfg.getString("gui.border-material"), Material.CYAN_STAINED_GLASS_PANE);
    }

    private Sound parseSound(String raw, Sound fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Sound.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown sound '" + raw + "', falling back to " + fallback);
            return fallback;
        }
    }

    private Particle parseParticle(String raw, Particle fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Particle.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown particle '" + raw + "', falling back to " + fallback);
            return fallback;
        }
    }

    private Material parseMaterial(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        Material mat = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
        return mat != null ? mat : fallback;
    }

    public double getDurabilityPerXp() {
        return durabilityPerXp;
    }

    public void setDurabilityPerXp(double durabilityPerXp) {
        this.durabilityPerXp = Math.max(0.1, durabilityPerXp);
        plugin.getConfig().set("repair.durability-per-xp", this.durabilityPerXp);
        plugin.saveConfig();
    }

    public boolean isRequireMending() {
        return requireMending;
    }

    public double getNonMendingMultiplier() {
        return nonMendingMultiplier;
    }

    public boolean isAllowPartialRepair() {
        return allowPartialRepair;
    }

    public int getMinXpPerRepair() {
        return minXpPerRepair;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public boolean isSneakClickToRepair() {
        return sneakClickToRepair;
    }

    public boolean isBypassEnabled() {
        return bypassEnabled;
    }

    public boolean isOpBypassesCost() {
        return opBypassesCost;
    }

    public boolean isOpBypassesMending() {
        return opBypassesMending;
    }

    public boolean isOpBypassesCooldown() {
        return opBypassesCooldown;
    }

    public boolean isSoundsEnabled() {
        return soundsEnabled;
    }

    public Sound getSoundRepairSuccess() {
        return soundRepairSuccess;
    }

    public float getVolRepairSuccess() {
        return volRepairSuccess;
    }

    public float getPitchRepairSuccess() {
        return pitchRepairSuccess;
    }

    public Sound getSoundRepairAll() {
        return soundRepairAll;
    }

    public float getVolRepairAll() {
        return volRepairAll;
    }

    public float getPitchRepairAll() {
        return pitchRepairAll;
    }

    public Sound getSoundNoDamage() {
        return soundNoDamage;
    }

    public float getVolNoDamage() {
        return volNoDamage;
    }

    public float getPitchNoDamage() {
        return pitchNoDamage;
    }

    public Sound getSoundNoMending() {
        return soundNoMending;
    }

    public float getVolNoMending() {
        return volNoMending;
    }

    public float getPitchNoMending() {
        return pitchNoMending;
    }

    public Sound getSoundInsufficientXp() {
        return soundInsufficientXp;
    }

    public float getVolInsufficientXp() {
        return volInsufficientXp;
    }

    public float getPitchInsufficientXp() {
        return pitchInsufficientXp;
    }

    public Sound getSoundGuiClick() {
        return soundGuiClick;
    }

    public float getVolGuiClick() {
        return volGuiClick;
    }

    public float getPitchGuiClick() {
        return pitchGuiClick;
    }

    public Sound getSoundGuiOpen() {
        return soundGuiOpen;
    }

    public float getVolGuiOpen() {
        return volGuiOpen;
    }

    public float getPitchGuiOpen() {
        return pitchGuiOpen;
    }

    public boolean isParticlesEnabled() {
        return particlesEnabled;
    }

    public Particle getParticleType() {
        return particleType;
    }

    public int getParticleCount() {
        return particleCount;
    }

    public String getGuiTitle() {
        return guiTitle;
    }

    public boolean isGuiFillEmptySlots() {
        return guiFillEmptySlots;
    }

    public Material getGuiFillMaterial() {
        return guiFillMaterial;
    }

    public Material getGuiBorderMaterial() {
        return guiBorderMaterial;
    }
}
