package dev.superseller.combattag.display;

import dev.superseller.combattag.combat.CombatManager;
import dev.superseller.combattag.combat.CombatTagEntry;
import dev.superseller.combattag.config.Messages;
import dev.superseller.combattag.config.PluginConfig;
import dev.superseller.combattag.scheduler.PlatformScheduler;
import dev.superseller.combattag.util.TimeUtil;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Renders the combat countdown as a boss bar, an action bar and (optionally) a title.
 * A single repeating task drives every display, Folia-safe through {@link PlatformScheduler}.
 */
public final class DisplayManager {

    private final PluginConfig config;
    private final Messages messages;
    private final CombatManager combat;

    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private Runnable taskCanceller;

    public DisplayManager(PluginConfig config, Messages messages, CombatManager combat) {
        this.config = config;
        this.messages = messages;
        this.combat = combat;
    }

    /** Starts the repeating render task. */
    public void start() {
        stop();
        int interval = config.getDisplayIntervalTicks();
        taskCanceller = PlatformScheduler.runTimer(interval, interval, this::tick);
    }

    /** Stops rendering and removes every boss bar. */
    public void stop() {
        if (taskCanceller != null) {
            taskCanceller.run();
            taskCanceller = null;
        }
        for (UUID id : Map.copyOf(bars).keySet()) {
            clear(id);
        }
    }

    private void tick() {
        combat.purgeExpired();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, CombatTagEntry> entry : combat.snapshot().entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            render(player, entry.getValue(), now);
        }
    }

    private void render(Player player, CombatTagEntry entry, long now) {
        String seconds = String.valueOf(entry.remainingSeconds(now));
        String pretty = TimeUtil.format(entry.remainingMillis(now));
        String opponent = entry.opponent() == null ? "-" : nameOf(entry.opponent());
        Map<String, String> ph = Map.of("seconds", seconds, "time", pretty, "opponent", opponent);

        if (config.isActionBarEnabled()) {
            player.sendActionBar(messages.get("display.action-bar", ph));
        }
        if (config.isBossBarEnabled()) {
            BossBar bar = bars.computeIfAbsent(player.getUniqueId(), id -> {
                BossBar created = BossBar.bossBar(Component.empty(), 1.0F, color(), overlay());
                player.showBossBar(created);
                return created;
            });
            bar.name(messages.get("display.boss-bar", ph));
            bar.progress(entry.progress(now));
            bar.color(color());
            bar.overlay(overlay());
        }
    }

    /** Shows the tag-start title if enabled. */
    public void showTagTitle(Player player, CombatTagEntry entry) {
        if (!config.isTitleOnTagEnabled() || player == null) {
            return;
        }
        Map<String, String> ph = Map.of(
                "seconds", String.valueOf(entry.remainingSeconds(System.currentTimeMillis())),
                "time", TimeUtil.format(entry.totalMillis()),
                "opponent", entry.opponent() == null ? "-" : nameOf(entry.opponent()));
        player.showTitle(Title.title(
                messages.get("display.title", ph),
                messages.get("display.subtitle", ph),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400))));
    }

    /** Removes any boss bar and clears the action bar for a player. */
    public void clear(UUID id) {
        BossBar bar = bars.remove(id);
        Player player = Bukkit.getPlayer(id);
        if (bar != null && player != null) {
            player.hideBossBar(bar);
        }
        if (player != null && config.isActionBarEnabled()) {
            player.sendActionBar(Component.empty());
        }
    }

    private static String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            return p.getName();
        }
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name != null ? name : "?";
    }

    private BossBar.Color color() {
        try {
            return BossBar.Color.valueOf(config.getBossBarColor().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return BossBar.Color.RED;
        }
    }

    private BossBar.Overlay overlay() {
        try {
            return BossBar.Overlay.valueOf(config.getBossBarOverlay().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return BossBar.Overlay.PROGRESS;
        }
    }
}
