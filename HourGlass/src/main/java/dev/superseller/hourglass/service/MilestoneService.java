package dev.superseller.hourglass.service;

import java.time.Duration;
import java.util.List;

import dev.superseller.hourglass.HourGlassPlugin;
import dev.superseller.hourglass.api.PlayerMilestoneReachEvent;
import dev.superseller.hourglass.config.HourGlassConfig;
import dev.superseller.hourglass.config.Messages;
import dev.superseller.hourglass.data.Milestone;
import dev.superseller.hourglass.data.PlaytimeRecord;
import dev.superseller.hourglass.scheduler.PlatformScheduler;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Awards configured playtime milestones: chat message, optional broadcast,
 * sound, title and console commands.
 *
 * <p>Thresholds are checked against the configured {@code milestones.metric}
 * ({@code total}, {@code active} or {@code primary}) at most once per
 * {@code milestones.check-seconds}, and also right after a join and after an
 * admin edit, so a manually granted "100 hours" still throws a party.
 *
 * <p>Awards are remembered by name in the player's file, so a milestone fires
 * exactly once per player even across restarts.
 */
public final class MilestoneService {

    private final HourGlassPlugin plugin;
    private final PlaytimeService service;
    private volatile long lastCheckMillis;
    private volatile int awardedTotal;

    public MilestoneService(HourGlassPlugin plugin, PlaytimeService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public int awardedTotal() {
        return awardedTotal;
    }

    /** Throttled entry point from the shared timer. */
    public void tick() {
        HourGlassConfig config = plugin.config();
        if (!config.milestonesEnabled() || config.milestones().isEmpty()) {
            return;
        }
        long interval = config.milestoneCheckSeconds() * 1_000L;
        long now = System.currentTimeMillis();
        if (now - lastCheckMillis < interval) {
            return;
        }
        lastCheckMillis = now;
        checkOnline();
    }

    /** Checks every online player once. */
    public void checkOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlaytimeRecord record = service.of(player.getUniqueId());
            if (record != null) {
                checkRecord(record, player);
            }
        }
    }

    /** Checks one player, e.g. after {@code /playtimeadmin add}. */
    public void check(PlaytimeRecord record) {
        checkRecord(record, record == null ? null : Bukkit.getPlayer(record.id()));
    }

    private void checkRecord(PlaytimeRecord record, Player player) {
        HourGlassConfig config = plugin.config();
        if (!config.milestonesEnabled() || record == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long seconds = service.metric(record, config.milestoneMetric(), now);
        for (Milestone milestone : config.milestones()) {
            if (seconds < milestone.seconds() || record.awardedMilestones().contains(milestone.name())) {
                continue;
            }
            award(player, record, milestone, seconds);
        }
    }

    private void award(Player player, PlaytimeRecord record, Milestone milestone, long seconds) {
        if (!record.award(milestone.name())) {
            return; // already granted, e.g. by a concurrent check
        }
        awardedTotal++;
        service.persist(record);

        Messages messages = plugin.messages();
        var placeholders = Messages.ph(
                "player", record.name() == null ? "" : record.name(),
                "milestone", milestone.name(),
                "threshold", plugin.config().timeFormat().format(milestone.seconds()),
                "time", plugin.config().timeFormat().format(seconds),
                "hours", formatHours(milestone.seconds()));

        PlayerMilestoneReachEvent event = new PlayerMilestoneReachEvent(player, milestone, seconds,
                shouldBroadcast(milestone), milestone.commands());
        try {
            Bukkit.getPluginManager().callEvent(event);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("A listener of PlayerMilestoneReachEvent threw: " + e.getMessage());
        }
        if (event.isCancelled()) {
            if (plugin.config().debug()) {
                plugin.getLogger().info("[milestones] " + milestone.name() + " for " + record.name()
                        + " was cancelled by another plugin");
            }
            return;
        }

        String messageKey = milestone.message() == null || milestone.message().isBlank()
                ? "milestones.reached" : milestone.message();
        if (player != null) {
            messages.send(player, messageKey, placeholders);
            plugin.sounds().play(player, milestone.sound() == null ? "milestone" : milestone.sound());
            if (plugin.config().milestoneTitle() && milestone.title()) {
                showTitle(player, placeholders);
            }
        }
        if (event.isBroadcast()) {
            messages.broadcast("milestones.broadcast", placeholders);
        }
        runCommands(event.getCommands(), record, milestone);
    }

    private void showTitle(Player player, java.util.Map<String, String> placeholders) {
        Messages messages = plugin.messages();
        Component title = messages.component("milestones.title", placeholders);
        Component subtitle = messages.component("milestones.subtitle", placeholders);
        PlatformScheduler.runForPlayer(player, () -> player.showTitle(Title.title(title, subtitle,
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600)))));
    }

    private void runCommands(List<String> commands, PlaytimeRecord record, Milestone milestone) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        PlatformScheduler.runGlobal(() -> {
            for (String raw : commands) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String command = raw.replace("%player%", record.name() == null ? "" : record.name())
                        .replace("%uuid%", record.id().toString())
                        .replace("%milestone%", milestone.name())
                        .replace("%hours%", formatHours(milestone.seconds()));
                if (command.startsWith("/")) {
                    command = command.substring(1);
                }
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Milestone command failed: /" + command + " (" + e.getMessage() + ")");
                }
            }
        });
    }

    /** The next milestone a player has not reached yet, or {@code null}. */
    public Milestone nextFor(PlaytimeRecord record) {
        if (record == null) {
            return null;
        }
        HourGlassConfig config = plugin.config();
        long seconds = service.metric(record, config.milestoneMetric(), System.currentTimeMillis());
        for (Milestone milestone : config.milestones()) {
            if (!record.awardedMilestones().contains(milestone.name())) {
                return milestone;
            }
        }
        return null;
    }

    /** How far the player is towards {@code next}, as 0..1. */
    public double progress(PlaytimeRecord record, Milestone next) {
        if (record == null || next == null || next.seconds() <= 0L) {
            return 1.0d;
        }
        long seconds = service.metric(record, plugin.config().milestoneMetric(), System.currentTimeMillis());
        return Math.max(0.0d, Math.min(1.0d, (double) seconds / (double) next.seconds()));
    }

    /** Number of configured milestones this player already has. */
    public int reached(PlaytimeRecord record) {
        if (record == null) {
            return 0;
        }
        int count = 0;
        for (Milestone milestone : plugin.config().milestones()) {
            if (record.awardedMilestones().contains(milestone.name())) {
                count++;
            }
        }
        return count;
    }

    private static String formatHours(long seconds) {
        long hours = seconds / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        if (minutes == 0L) {
            return String.valueOf(hours);
        }
        return hours + "h" + (minutes < 10 ? "0" : "") + minutes;
    }

    /** {@code milestones.broadcast} plus the per-milestone override. */
    private boolean shouldBroadcast(Milestone milestone) {
        return plugin.config().milestoneBroadcast() && milestone.broadcast();
    }
}
