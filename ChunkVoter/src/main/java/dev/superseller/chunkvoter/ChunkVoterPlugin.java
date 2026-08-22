package dev.superseller.chunkvoter;

import dev.superseller.chunkvoter.command.ChunkVoteAdminCommand;
import dev.superseller.chunkvoter.command.ChunkVoteCommand;
import dev.superseller.chunkvoter.config.ChunkVoterConfig;
import dev.superseller.chunkvoter.config.Messages;
import dev.superseller.chunkvoter.hook.WorldGuardHook;
import dev.superseller.chunkvoter.listener.PlayerQuitListener;
import dev.superseller.chunkvoter.scheduler.PlatformScheduler;
import dev.superseller.chunkvoter.vote.VoteManager;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ChunkVoter — community-voted chunk regeneration for Minecraft 26.2
 * (Paper / Purpur / Folia), with optional WorldGuard owner control.
 */
public final class ChunkVoterPlugin extends JavaPlugin {

    public static final String VERSION = "1.0.0";

    private ChunkVoterConfig config;
    private Messages messages;
    private WorldGuardHook worldGuard;
    private VoteManager voteManager;

    @Override
    public void onEnable() {
        getLogger().info("Enabling ChunkVoter v" + VERSION + " ...");

        PlatformScheduler.init(this);
        saveDefaultConfig();

        config = new ChunkVoterConfig(this);
        config.load();

        messages = new Messages(this);
        messages.load();

        worldGuard = new WorldGuardHook(this);
        worldGuard.init(config);

        voteManager = new VoteManager(this);
        voteManager.start();

        registerCommands();
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);

        getLogger().info("ChunkVoter enabled. Vote duration: " + config.durationSeconds()
                + "s, cooldown: " + config.cooldownSeconds() + "s, WorldGuard hook: " + worldGuard.describe());
    }

    private void registerCommands() {
        PluginCommand cv = getCommand("chunkvoter");
        if (cv != null) {
            ChunkVoteCommand exec = new ChunkVoteCommand(this);
            cv.setExecutor(exec);
            cv.setTabCompleter(exec);
        }
        PluginCommand cva = getCommand("chunkvoteadmin");
        if (cva != null) {
            ChunkVoteAdminCommand exec = new ChunkVoteAdminCommand(this);
            cva.setExecutor(exec);
            cva.setTabCompleter(exec);
        }
    }

    /** Reloads config/messages and re-detects WorldGuard. */
    public void reload() {
        reloadConfig();
        config.load();
        messages.load();
        worldGuard.init(config);
        getLogger().info("ChunkVoter configuration reloaded.");
    }

    @Override
    public void onDisable() {
        if (voteManager != null) {
            voteManager.stop();
        }
        getLogger().info("ChunkVoter disabled. Active votes cleared.");
    }

    public ChunkVoterConfig configuration() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public WorldGuardHook worldGuard() {
        return worldGuard;
    }

    public VoteManager voteManager() {
        return voteManager;
    }
}
