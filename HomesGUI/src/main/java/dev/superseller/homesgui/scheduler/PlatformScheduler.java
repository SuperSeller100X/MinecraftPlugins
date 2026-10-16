package dev.superseller.homesgui.scheduler;

import java.util.concurrent.TimeUnit;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/** Thin Paper/Folia scheduler facade: entity work follows its player; file work is async. */
public final class PlatformScheduler {

    private PlatformScheduler() {
    }

    /** Schedule player-facing work on the player's owning entity/region scheduler. */
    public static void runForPlayer(JavaPlugin plugin, Player player, Runnable action) {
        if (player == null) {
            return;
        }
        player.getScheduler().run(plugin, task -> action.run(), () -> { });
    }

    /** Run storage work away from tick threads. Do not access live Bukkit entities here. */
    public static void runAsync(JavaPlugin plugin, Runnable action) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> action.run());
    }

    /** Background autosave timer, cancelled by the owner during plugin shutdown. */
    public static ScheduledTask repeatAsync(JavaPlugin plugin, Runnable action, long seconds) {
        long interval = Math.max(1L, seconds);
        return plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin,
                task -> action.run(), interval, interval, TimeUnit.SECONDS);
    }
}
