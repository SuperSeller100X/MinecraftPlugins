package dev.superseller.attag;

import dev.superseller.attag.config.AtTagConfig;
import dev.superseller.attag.listener.ChatListener;
import dev.superseller.attag.scheduler.PlatformScheduler;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * AtTag — @-mentions for Minecraft 26.2 (Paper / Purpur / Folia).
 *
 * <ul>
 *   <li>{@code @playername} — pings the player with a sound.</li>
 *   <li>{@code @here} — replaced with the sender's coordinates [x, y, z].</li>
 *   <li>{@code @everyone} / {@code @all} — pings every online player.</li>
 * </ul>
 *
 * No commands, no permissions — everyone can use every mention.
 */
public final class AtTagPlugin extends JavaPlugin {

    public static final String VERSION = "1.0.0";

    private AtTagConfig config;

    @Override
    public void onEnable() {
        config = new AtTagConfig(this);
        config.load();

        PlatformScheduler.init(this);
        getServer().getPluginManager().registerEvents(new ChatListener(config), this);

        getLogger().info("AtTag v" + VERSION
                + " enabled — @player pings, @here coordinates, @everyone/@all ready.");
    }

    @Override
    public void onDisable() {
        getLogger().info("AtTag v" + VERSION + " disabled.");
    }

    public AtTagConfig getAtTagConfig() {
        return config;
    }
}
