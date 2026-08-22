package dev.superseller.chunkvoter.config;

import dev.superseller.chunkvoter.ChunkVoterPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed access to config.yml.
 */
public final class ChunkVoterConfig {

    private final ChunkVoterPlugin plugin;

    private long durationSeconds;
    private long cooldownSeconds;
    private int maxActivePerWorld;
    private boolean announce;
    private boolean autoCancelOnQuit;

    private boolean wgEnabled;
    private boolean wgRequireOwner;
    private boolean wgAdminsBypass;

    private boolean worldEditEnabled;
    private boolean worldEditRequired;

    private boolean anvilEnabled;
    private boolean anvilSafeTeleport;

    private boolean requireChunkLoad;

    public ChunkVoterConfig(ChunkVoterPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration c = plugin.getConfig();

        durationSeconds = Math.max(5, c.getLong("vote.duration-seconds", 30));
        cooldownSeconds = Math.max(0, c.getLong("vote.cooldown-seconds", 120));
        maxActivePerWorld = Math.max(1, c.getInt("vote.max-active-per-world", 5));
        announce = c.getBoolean("vote.announce", true);
        autoCancelOnQuit = c.getBoolean("vote.auto-cancel-on-quit", true);

        String wgMode = mode(c.getString("worldguard.mode", "auto"));
        wgRequireOwner = c.getBoolean("worldguard.require-owner", true);
        wgAdminsBypass = c.getBoolean("worldguard.admins-bypass", true);
        wgEnabled = !"false".equals(wgMode);

        String worldEditMode = mode(c.getString("worldedit.mode", "auto"));
        worldEditEnabled = !"false".equals(worldEditMode);
        worldEditRequired = "true".equals(worldEditMode);

        String anvilMode = mode(c.getString("anvil.mode", "auto"));
        anvilEnabled = !"false".equals(anvilMode);
        anvilSafeTeleport = c.getBoolean("anvil.safe-teleport", true);

        requireChunkLoad = c.getBoolean("regenerate.require-chunk-load", true);
    }

    public long durationSeconds() {
        return durationSeconds;
    }

    public long durationTicks() {
        return Math.max(1L, durationSeconds * 20L);
    }

    public long cooldownSeconds() {
        return cooldownSeconds;
    }

    public int maxActivePerWorld() {
        return maxActivePerWorld;
    }

    public boolean announce() {
        return announce;
    }

    public boolean autoCancelOnQuit() {
        return autoCancelOnQuit;
    }

    public boolean wgEnabled() {
        return wgEnabled;
    }

    public boolean wgRequireOwner() {
        return wgRequireOwner;
    }

    public boolean wgAdminsBypass() {
        return wgAdminsBypass;
    }

    public boolean worldEditEnabled() {
        return worldEditEnabled;
    }

    public boolean worldEditRequired() {
        return worldEditRequired;
    }

    public boolean anvilEnabled() {
        return anvilEnabled;
    }

    public boolean anvilSafeTeleport() {
        return anvilSafeTeleport;
    }

    public boolean requireChunkLoad() {
        return requireChunkLoad;
    }

    private static String mode(String value) {
        if (value == null) {
            return "auto";
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "true", "false", "auto" -> normalized;
            default -> "auto";
        };
    }
}
