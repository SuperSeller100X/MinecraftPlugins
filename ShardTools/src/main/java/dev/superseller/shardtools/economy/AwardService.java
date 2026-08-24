package dev.superseller.shardtools.economy;

import java.util.Collection;

import org.bukkit.Sound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import dev.superseller.shardtools.ShardToolsPlugin;
import dev.superseller.shardtools.scheduler.PlatformScheduler;
import dev.superseller.shardtools.util.Numbers;

/**
 * Periodically awards shards to every player currently on the server
 * (default: 5 shards every 5 minutes - fully configurable).
 */
public final class AwardService {

    private final ShardToolsPlugin plugin;

    public AwardService(ShardToolsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Called by the repeating task on the global region thread. */
    public void tick() {
        long amount = plugin.effectiveAwardAmount();
        if (amount <= 0L) {
            return;
        }
        long minutes = plugin.effectiveAwardIntervalMinutes();
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        for (Player player : online) {
            plugin.accounts().add(player.getUniqueId(), player.getName(), amount);
            if (plugin.settings().awardAnnounce()) {
                PlatformScheduler.runEntity(player, () -> {
                    plugin.messages().send(player, "award.received",
                            "%amount%", Numbers.format(amount),
                            "%minutes%", Long.toString(minutes),
                            "%symbol%", plugin.settings().symbol());
                    Sound sound = plugin.soundResolver().resolve(plugin.settings().soundAward());
                    if (sound != null) {
                        player.playSound(player.getLocation(), sound, 0.8f, 1.4f);
                    }
                });
            }
        }
        if (!online.isEmpty()) {
            plugin.accounts().saveAsync();
        }
    }
}
