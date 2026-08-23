package dev.superseller.teleportsigns.config;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Typed view of {@code config.yml}. */
public final class PluginSettings {

    private final JavaPlugin plugin;

    private int maxLookDistance = 6;
    private int listRadius = 32;
    private int listMax = 20;
    private boolean waxOnBind = true;
    private boolean sneakToEdit = true;
    private Sound sound;
    private Particle particle;

    private boolean safetyEnabled = true;
    private boolean checkLava = true;
    private boolean checkFire = true;
    private boolean requireSolidBelow = true;
    private boolean rejectVoid = true;
    private int maxFall = 4;

    private double cooldownSeconds = 3.0d;
    private double warmupSeconds = 0.0d;
    private boolean cancelOnMove = true;
    private boolean cancelOnDamage = true;

    private String economyMode = "auto";
    private double defaultCost = 0.0d;
    private String missingPolicy = "allow";

    public PluginSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        maxLookDistance = Math.max(1, cfg.getInt("settings.max-look-distance", 6));
        listRadius = Math.max(1, cfg.getInt("settings.list-radius", 32));
        listMax = Math.max(1, cfg.getInt("settings.list-max", 20));
        waxOnBind = cfg.getBoolean("settings.wax-on-bind", true);
        sneakToEdit = cfg.getBoolean("settings.sneak-to-edit", true);
        sound = parseSound(cfg.getString("settings.sound", "ENTITY_ENDERMAN_TELEPORT"));
        particle = parseParticle(cfg.getString("settings.particle", "PORTAL"));

        safetyEnabled = cfg.getBoolean("safety.enabled", true);
        checkLava = cfg.getBoolean("safety.check-lava", true);
        checkFire = cfg.getBoolean("safety.check-fire", true);
        requireSolidBelow = cfg.getBoolean("safety.require-solid-below", true);
        rejectVoid = cfg.getBoolean("safety.reject-void", true);
        maxFall = Math.max(1, cfg.getInt("safety.max-fall", 4));

        cooldownSeconds = Math.max(0.0d, cfg.getDouble("cooldown.seconds", 3.0d));
        warmupSeconds = Math.max(0.0d, cfg.getDouble("warmup.seconds", 0.0d));
        cancelOnMove = cfg.getBoolean("warmup.cancel-on-move", true);
        cancelOnDamage = cfg.getBoolean("warmup.cancel-on-damage", true);

        String mode = cfg.getString("economy.enabled", "auto");
        economyMode = mode == null ? "auto" : mode.trim();
        defaultCost = Math.max(0.0d, cfg.getDouble("economy.default-cost", 0.0d));
        String policy = cfg.getString("economy.missing-policy", "allow");
        missingPolicy = policy == null ? "allow" : policy.trim().toLowerCase();
    }

    private Sound parseSound(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Sound.valueOf(raw.trim().toUpperCase().replace('.', '_').replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown sound '" + raw + "', sound disabled.");
            return null;
        }
    }

    private Particle parseParticle(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Particle.valueOf(raw.trim().toUpperCase().replace('.', '_').replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown particle '" + raw + "', particles disabled.");
            return null;
        }
    }

    public int maxLookDistance() {
        return maxLookDistance;
    }

    public int listRadius() {
        return listRadius;
    }

    public int listMax() {
        return listMax;
    }

    public boolean waxOnBind() {
        return waxOnBind;
    }

    public boolean sneakToEdit() {
        return sneakToEdit;
    }

    public Sound sound() {
        return sound;
    }

    public Particle particle() {
        return particle;
    }

    public boolean safetyEnabled() {
        return safetyEnabled;
    }

    public boolean checkLava() {
        return checkLava;
    }

    public boolean checkFire() {
        return checkFire;
    }

    public boolean requireSolidBelow() {
        return requireSolidBelow;
    }

    public boolean rejectVoid() {
        return rejectVoid;
    }

    public int maxFall() {
        return maxFall;
    }

    public double cooldownSeconds() {
        return cooldownSeconds;
    }

    public long cooldownMillis() {
        return Math.round(cooldownSeconds * 1000.0d);
    }

    public double warmupSeconds() {
        return warmupSeconds;
    }

    public long warmupTicks() {
        return Math.max(0L, Math.round(warmupSeconds * 20.0d));
    }

    public boolean cancelOnMove() {
        return cancelOnMove;
    }

    public boolean cancelOnDamage() {
        return cancelOnDamage;
    }

    public String economyMode() {
        return economyMode;
    }

    public double defaultCost() {
        return defaultCost;
    }

    public boolean denyWhenEconomyMissing() {
        return "deny".equalsIgnoreCase(missingPolicy);
    }
}
