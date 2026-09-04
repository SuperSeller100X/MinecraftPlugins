package dev.superseller.hourglass.service;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.superseller.hourglass.HourGlassPlugin;
import dev.superseller.hourglass.config.HourGlassConfig;
import dev.superseller.hourglass.config.Messages;
import dev.superseller.hourglass.data.Milestone;
import dev.superseller.hourglass.data.PlaytimeRecord;
import dev.superseller.hourglass.scheduler.PlatformScheduler;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * The optional live timer: a boss bar or an action bar that ticks while the
 * player is connected.
 *
 * <p>{@code display.mode} is the server-wide default and each player can
 * override it ({@code /playtime display ...}), which is stored per player.
 * Rendering happens on the player's own region thread, so it is legal on Folia,
 * and a bar is always removed on quit, on mode change and on reload so nothing
 * can be left stuck on someone's screen.
 */
public final class DisplayService {

    /** The modes a player may pick, cycled by the GUI button. */
    public static final PlaytimeRecord.DisplayMode[] CYCLE = {
            PlaytimeRecord.DisplayMode.BOSSBAR,
            PlaytimeRecord.DisplayMode.ACTIONBAR,
            PlaytimeRecord.DisplayMode.OFF,
            PlaytimeRecord.DisplayMode.INHERIT
    };

    private final HourGlassPlugin plugin;
    private final PlaytimeService service;
    private final MilestoneService milestones;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final Map<PlaytimeRecord.DisplayMode, String> labelCache = new EnumMap<>(PlaytimeRecord.DisplayMode.class);
    private volatile long lastUpdateMillis;

    public DisplayService(HourGlassPlugin plugin, PlaytimeService service, MilestoneService milestones) {
        this.plugin = plugin;
        this.service = service;
        this.milestones = milestones;
    }

    /** Throttled update for every online player. */
    public void tick() {
        int updateSeconds = plugin.config().displayUpdateSeconds();
        long now = System.currentTimeMillis();
        if (now - lastUpdateMillis < updateSeconds * 1_000L) {
            return;
        }
        lastUpdateMillis = now;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            update(player);
        }
    }

    /** Resolves what this player should actually see. */
    public String effectiveMode(PlaytimeRecord record) {
        PlaytimeRecord.DisplayMode mode = record == null ? PlaytimeRecord.DisplayMode.INHERIT : record.display();
        if (mode == PlaytimeRecord.DisplayMode.INHERIT) {
            String configured = plugin.config().displayMode();
            return "both".equals(configured) ? "bossbar" : configured;
        }
        return mode.name().toLowerCase(Locale.US);
    }

    /** Refreshes (or removes) the live display of one player. */
    public void update(Player player) {
        if (player == null) {
            return;
        }
        PlaytimeRecord record = service.of(player.getUniqueId());
        String mode = effectiveMode(record);
        if (record == null || "none".equals(mode) || "off".equals(mode)) {
            PlatformScheduler.runForPlayer(player, () -> hideBar(player));
            return;
        }
        long now = System.currentTimeMillis();
        long seconds = service.metric(record, plugin.config().displayMetric(), now);
        Map<String, String> placeholders = Messages.ph(
                "time", plugin.config().timeFormat().format(seconds),
                "total", plugin.config().timeFormat().format(record.totalSeconds()),
                "active", plugin.config().timeFormat().format(record.activeSeconds()),
                "session", plugin.config().timeFormat().format(record.sessionTotalSeconds(now)),
                "player", player.getName(),
                "seconds", String.valueOf(seconds),
                "idle", plugin.tracking().idle(player.getUniqueId()) ? "yes" : "no");

        if ("actionbar".equals(mode)) {
            PlatformScheduler.runForPlayer(player, () -> plugin.messages().action(player, "display.actionbar",
                    placeholders));
            PlatformScheduler.runForPlayer(player, () -> hideBar(player));
            return;
        }
        if (!"bossbar".equals(mode)) {
            return;
        }
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(),
                id -> BossBar.bossBar(Component.empty(), 1.0f, color(), overlay()));
        bar.name(plugin.messages().has("display.bossbar")
                ? plugin.messages().component("display.bossbar", placeholders)
                : Component.text("Playtime: " + placeholders.get("time")));
        bar.progress(progress(record));
        bar.color(color());
        bar.overlay(overlay());
        PlatformScheduler.runForPlayer(player, () -> player.showBossBar(bar));
    }

    /** 0..1 fill for the bar, per {@code display.bossbar.progress}. */
    private float progress(PlaytimeRecord record) {
        String mode = plugin.config().bossBarProgress();
        long now = System.currentTimeMillis();
        switch (mode) {
            case "week": {
                long weekSeconds = 7L * 86_400L;
                long into = record.totalSeconds() % weekSeconds;
                return clamp((double) into / weekSeconds);
            }
            case "milestone": {
                Milestone next = milestones.nextFor(record);
                if (next == null) {
                    return 1.0f;
                }
                return (float) milestones.progress(record, next);
            }
            case "none":
                return 1.0f;
            case "day":
            default: {
                long daySeconds = 86_400L;
                long into = record.totalSeconds() % daySeconds;
                return clamp((double) into / daySeconds);
            }
        }
    }

    private static float clamp(double value) {
        return (float) Math.max(0.0d, Math.min(1.0d, value));
    }

    private BossBar.Color color() {
        try {
            return BossBar.Color.valueOf(plugin.config().bossBarColor().trim().toUpperCase(Locale.US));
        } catch (RuntimeException e) {
            return BossBar.Color.PURPLE;
        }
    }

    private BossBar.Overlay overlay() {
        try {
            return BossBar.Overlay.valueOf(plugin.config().bossBarOverlay().trim().toUpperCase(Locale.US));
        } catch (RuntimeException e) {
            return BossBar.Overlay.NOTCHED_10;
        }
    }

    /** Removes the bar for a player who switched modes or left. */
    public void hideBar(Player player) {
        if (player == null) {
            return;
        }
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            try {
                player.hideBossBar(bar);
            } catch (RuntimeException ignored) {
                // player already disconnected; nothing to hide
            }
        }
    }

    /** Removes the bar by id, for quit handling off the region thread. */
    public void clear(UUID id) {
        if (id == null) {
            return;
        }
        BossBar bar = bars.remove(id);
        Player player = plugin.getServer().getPlayer(id);
        if (bar != null && player != null) {
            PlatformScheduler.runForPlayer(player, () -> player.hideBossBar(bar));
        }
        if (player != null) {
            PlatformScheduler.runForPlayer(player, () -> player.sendActionBar(net.kyori.adventure.text.Component.empty()));
        }
    }

    /** Hides everything (reload, disable). */
    public void clearAll() {
        for (Map.Entry<UUID, BossBar> entry : bars.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) {
                BossBar bar = entry.getValue();
                PlatformScheduler.runForPlayer(player, () -> player.hideBossBar(bar));
            }
        }
        bars.clear();
    }

    /** Cycles this player's personal display preference and saves it. */
    public PlaytimeRecord.DisplayMode cycle(Player player) {
        PlaytimeRecord record = service.getOrCreate(player.getUniqueId(), player.getName());
        PlaytimeRecord.DisplayMode current = record.display();
        int index = 0;
        for (int i = 0; i < CYCLE.length; i++) {
            if (CYCLE[i] == current) {
                index = i;
                break;
            }
        }
        PlaytimeRecord.DisplayMode next = CYCLE[(index + 1) % CYCLE.length];
        record.display(next);
        service.persist(record);
        hideBar(player);
        update(player);
        return next;
    }

    /** Sets an explicit mode; {@code null} resets to "follow the config". */
    public PlaytimeRecord.DisplayMode set(Player player, PlaytimeRecord.DisplayMode mode) {
        PlaytimeRecord record = service.getOrCreate(player.getUniqueId(), player.getName());
        record.display(mode == null ? PlaytimeRecord.DisplayMode.INHERIT : mode);
        service.persist(record);
        hideBar(player);
        update(player);
        return record.display();
    }

    /** Human-readable label of a display mode, for GUI lines. */
    public String label(PlaytimeRecord.DisplayMode mode) {
        return labelCache.computeIfAbsent(mode, key -> switch (key) {
            case BOSSBAR -> "bossbar";
            case ACTIONBAR -> "actionbar";
            case OFF -> "off";
            case INHERIT -> "inherit (" + plugin.config().displayMode() + ")";
        });
    }
}
