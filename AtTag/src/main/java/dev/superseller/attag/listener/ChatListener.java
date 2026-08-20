package dev.superseller.attag.listener;

import dev.superseller.attag.config.AtTagConfig;
import dev.superseller.attag.engine.PingEngine;
import dev.superseller.attag.scheduler.PlatformScheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Scans chat messages for @-mentions:
 * <ul>
 *   <li>{@code @playername} — the mentioned player hears the player-sound.</li>
 *   <li>{@code @here} — replaced with the sender's coordinates {@code [x, y, z]}.</li>
 *   <li>{@code @everyone} / {@code @all} — everyone except the sender hears
 *       the everyone-sound.</li>
 * </ul>
 *
 * Folia-safe: sounds are delivered through {@link PlatformScheduler}, which
 * hops to each target player's region thread.
 */
public final class ChatListener implements Listener {

    private final AtTagConfig config;

    public ChatListener(AtTagConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();
        if (message == null || message.indexOf('@') < 0) {
            return;
        }

        Player sender = event.getPlayer();
        Collection<? extends Player> online = sender.getServer().getOnlinePlayers();

        List<String> names = new ArrayList<>(online.size());
        Map<String, Player> byName = new HashMap<>();
        for (Player player : online) {
            names.add(player.getName());
            byName.put(player.getName().toLowerCase(Locale.ROOT), player);
        }

        Location loc = sender.getLocation();
        PingEngine.Result result = PingEngine.parse(
                message, names, sender.getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        // @here is rewritten even when nobody hears a ping sound.
        if (!result.message().equals(message)) {
            event.setMessage(result.message());
        }

        if (!result.hasPing()) {
            return;
        }

        for (String name : result.namedTargets()) {
            ping(byName.get(name.toLowerCase(Locale.ROOT)), config.playerSound());
        }
        for (String name : result.everyoneTargets()) {
            ping(byName.get(name.toLowerCase(Locale.ROOT)), config.everyoneSound());
        }
    }

    private void ping(Player target, Sound sound) {
        if (target == null) {
            return;
        }
        PlatformScheduler.runEntitySync(target, () -> {
            if (!target.isOnline()) {
                return;
            }
            target.playSound(target.getLocation(), sound, config.volume(), config.pitch());
        });
    }
}
