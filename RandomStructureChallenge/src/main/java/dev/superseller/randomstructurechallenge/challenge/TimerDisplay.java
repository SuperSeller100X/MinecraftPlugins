package dev.superseller.randomstructurechallenge.challenge;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

import dev.superseller.randomstructurechallenge.RandomStructureChallengePlugin;
import dev.superseller.randomstructurechallenge.config.Messages;
import dev.superseller.randomstructurechallenge.config.PluginSettings;
import dev.superseller.randomstructurechallenge.scheduler.PlatformScheduler;
import dev.superseller.randomstructurechallenge.util.TimerBar;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Hotbar (action-bar) countdown plus the percent BossBar.
 */
public final class TimerDisplay {

    private final RandomStructureChallengePlugin plugin;
    private final PluginSettings settings;
    private final Messages messages;

    private BossBar bossBar;

    public TimerDisplay(RandomStructureChallengePlugin plugin, PluginSettings settings, Messages messages) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
    }

    public void showCountdown(int remaining, int interval, boolean paused) {
        float progress = TimerBar.progress(remaining, interval);
        int percent = TimerBar.percent(remaining, interval);
        String clock = TimerBar.clock(remaining);
        String bar = TimerBar.bar(remaining, interval, settings.barWidth());
        Map<String, String> placeholders = Map.of(
                "seconds", Integer.toString(Math.max(0, remaining)),
                "interval", Integer.toString(interval),
                "clock", clock,
                "percent", Integer.toString(percent),
                "bar", bar
        );

        if (settings.bossbarEnabled()) {
            BossBar barHandle = ensureBossBar();
            barHandle.progress(Math.max(0f, Math.min(1f, progress)));
            barHandle.color(colorFor(progress, paused));
            barHandle.overlay(overlay());
            String titleKey = paused ? "bossbar-title-paused" : "bossbar-title";
            barHandle.name(messages.component(titleKey, placeholders));
        }

        String actionKey;
        if (paused) {
            actionKey = "action-bar-paused";
        } else if (remaining > 0 && remaining <= settings.lowThreshold()) {
            actionKey = "action-bar-low";
        } else {
            actionKey = "action-bar";
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlatformScheduler.runEntitySync(player, () -> {
                if (settings.bossbarEnabled() && bossBar != null) {
                    player.showBossBar(bossBar);
                }
                if (settings.actionbarEnabled()) {
                    player.sendActionBar(messages.component(actionKey, placeholders));
                }
                if (!paused && remaining > 0 && remaining <= settings.lowThreshold()) {
                    play(player, settings.soundTickLow(), 1.15f);
                }
            });
        }
    }

    public void flashSpawn(String structureKey) {
        String pretty = TimerBar.prettyStructure(structureKey);
        Map<String, String> placeholders = Map.of("structure", pretty, "name", pretty);
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlatformScheduler.runEntitySync(player, () -> {
                if (settings.actionbarEnabled()) {
                    player.sendActionBar(messages.component("action-bar-spawn", placeholders));
                }
                if (settings.titleOnSpawn()) {
                    player.showTitle(Title.title(
                            messages.component("title-spawn", placeholders),
                            messages.component("subtitle-spawn", placeholders),
                            Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(2), Duration.ofMillis(350))
                    ));
                }
                play(player, settings.soundSpawn(), 0.85f);
            });
        }
    }

    public void playToAll(String soundKey) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlatformScheduler.runEntitySync(player, () -> play(player, soundKey, settings.pitch()));
        }
    }

    public void hide() {
        BossBar bar = bossBar;
        if (bar == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlatformScheduler.runEntitySync(player, () -> player.hideBossBar(bar));
        }
    }

    public void showTo(Player player) {
        if (player == null || !settings.bossbarEnabled() || bossBar == null) {
            return;
        }
        PlatformScheduler.runEntitySync(player, () -> player.showBossBar(bossBar));
    }

    public void hideFrom(Player player) {
        if (player == null || bossBar == null) {
            return;
        }
        PlatformScheduler.runEntitySync(player, () -> player.hideBossBar(bossBar));
    }

    public void rebuild() {
        hide();
        bossBar = null;
    }

    private BossBar ensureBossBar() {
        if (bossBar == null) {
            bossBar = BossBar.bossBar(
                    messages.raw("seconds left until next structure: 0"),
                    1f,
                    colorFor(1f, false),
                    overlay()
            );
        }
        return bossBar;
    }

    private BossBar.Color colorFor(float progress, boolean paused) {
        if (paused) {
            return BossBar.Color.YELLOW;
        }
        if (progress > 0.5f) {
            return parseColor(settings.bossbarColor(), BossBar.Color.PURPLE);
        }
        if (progress > 0.2f) {
            return BossBar.Color.YELLOW;
        }
        return BossBar.Color.RED;
    }

    private BossBar.Overlay overlay() {
        String raw = settings.bossbarOverlay() == null ? "" : settings.bossbarOverlay().trim().toUpperCase(Locale.ROOT);
        try {
            return BossBar.Overlay.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return BossBar.Overlay.PROGRESS;
        }
    }

    private static BossBar.Color parseColor(String raw, BossBar.Color fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return BossBar.Color.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private void play(Player player, String key, float pitch) {
        if (!settings.soundsEnabled() || key == null || key.isBlank()) {
            return;
        }
        try {
            Key parsed = Key.key(key.contains(":") ? key : "minecraft:" + key);
            player.playSound(Sound.sound(parsed, Sound.Source.MASTER, settings.volume(), pitch));
        } catch (Throwable ignored) {
            plugin.getLogger().fine("Unknown sound '" + key + "'");
        }
    }
}
