package dev.superseller.randomstructurechallenge.challenge;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import dev.superseller.randomstructurechallenge.RandomStructureChallengePlugin;
import dev.superseller.randomstructurechallenge.config.Messages;
import dev.superseller.randomstructurechallenge.config.PluginSettings;
import dev.superseller.randomstructurechallenge.scheduler.PlatformScheduler;
import dev.superseller.randomstructurechallenge.util.TimerBar;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * One global challenge: countdown → random vanilla structure on every player.
 */
public final class ChallengeManager {

    public enum State {
        IDLE, AWAITING, RUNNING, PAUSED
    }

    private final RandomStructureChallengePlugin plugin;
    private final PluginSettings settings;
    private final Messages messages;
    private final StructureCatalog catalog;
    private final StructurePlacer placer;
    private final TimerDisplay display;

    private State state = State.IDLE;
    private UUID awaitingPlayer;
    private long awaitingDeadlineMillis;
    private String starterName = "CONSOLE";
    private UUID starterId;
    private int intervalSeconds;
    private int remainingSeconds;
    private int spawnCount;
    private String lastStructure = "-";

    public ChallengeManager(
            RandomStructureChallengePlugin plugin,
            PluginSettings settings,
            Messages messages,
            StructureCatalog catalog,
            StructurePlacer placer,
            TimerDisplay display) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.catalog = catalog;
        this.placer = placer;
        this.display = display;
    }

    public synchronized State state() {
        return state;
    }

    public synchronized boolean isAwaiting(UUID playerId) {
        return state == State.AWAITING && playerId != null && playerId.equals(awaitingPlayer);
    }

    public synchronized boolean isActive() {
        return state == State.RUNNING || state == State.PAUSED;
    }

    public void beginAwaiting(Player player) {
        synchronized (this) {
            if (isActive()) {
                messages.send(player, "already-running", Map.of("state", state.name().toLowerCase()));
                return;
            }
            if (state == State.AWAITING && awaitingPlayer != null && !awaitingPlayer.equals(player.getUniqueId())) {
                Player previous = Bukkit.getPlayer(awaitingPlayer);
                if (previous != null) {
                    messages.send(previous, "await-cancelled");
                }
            }
            state = State.AWAITING;
            awaitingPlayer = player.getUniqueId();
            awaitingDeadlineMillis = System.currentTimeMillis() + settings.inputTimeout() * 1000L;
        }
        messages.send(player, "await-prompt", Map.of("seconds", Integer.toString(settings.inputTimeout())));
    }

    public void start(CommandSender starter, int seconds) {
        if (seconds < settings.minInterval() || seconds > settings.maxInterval()) {
            String key = seconds < settings.minInterval() ? "interval-too-low" : "interval-too-high";
            messages.send(starter, key, Map.of(
                    "min", Integer.toString(settings.minInterval()),
                    "max", Integer.toString(settings.maxInterval()),
                    "input", Integer.toString(seconds)
            ));
            return;
        }
        synchronized (this) {
            if (isActive()) {
                messages.send(starter, "already-running", Map.of("state", state.name().toLowerCase()));
                return;
            }
            state = State.RUNNING;
            awaitingPlayer = null;
            awaitingDeadlineMillis = 0L;
            intervalSeconds = seconds;
            remainingSeconds = seconds;
            spawnCount = 0;
            lastStructure = "-";
            starterName = starter.getName();
            starterId = starter instanceof Player player ? player.getUniqueId() : null;
        }
        catalog.refresh();
        broadcast("started", Map.of(
                "player", starter.getName(),
                "interval", Integer.toString(seconds)
        ));
        display.playToAll(settings.soundStart());
        display.showCountdown(seconds, seconds, false);
        plugin.getLogger().info("Challenge started by " + starter.getName() + " (" + seconds + "s interval).");
    }

    public void stop(CommandSender actor) {
        int spawns;
        synchronized (this) {
            if (state == State.IDLE) {
                messages.send(actor, "not-running");
                return;
            }
            if (state == State.AWAITING) {
                cancelAwait(actor, true);
                return;
            }
            spawns = spawnCount;
            resetIdle();
        }
        display.hide();
        broadcast("stopped", Map.of(
                "player", actor.getName(),
                "spawns", Integer.toString(spawns)
        ));
        display.playToAll(settings.soundStop());
    }

    public void pause(CommandSender actor) {
        int remaining;
        synchronized (this) {
            if (state == State.PAUSED) {
                messages.send(actor, "already-paused");
                return;
            }
            if (state != State.RUNNING) {
                messages.send(actor, "not-running");
                return;
            }
            state = State.PAUSED;
            remaining = remainingSeconds;
        }
        display.showCountdown(remaining, intervalSeconds, true);
        broadcast("paused", Map.of(
                "player", actor.getName(),
                "seconds", Integer.toString(remaining)
        ));
        display.playToAll(settings.soundPause());
    }

    public void resume(CommandSender actor) {
        int remaining;
        synchronized (this) {
            if (state != State.PAUSED) {
                messages.send(actor, "not-paused");
                return;
            }
            state = State.RUNNING;
            remaining = remainingSeconds;
        }
        display.showCountdown(remaining, intervalSeconds, false);
        broadcast("resumed", Map.of(
                "player", actor.getName(),
                "seconds", Integer.toString(remaining)
        ));
        display.playToAll(settings.soundResume());
    }

    public void sendStatus(CommandSender sender) {
        State snapshot;
        String starter;
        int interval;
        int remaining;
        int spawns;
        String last;
        synchronized (this) {
            snapshot = state;
            starter = starterName;
            interval = intervalSeconds;
            remaining = remainingSeconds;
            spawns = spawnCount;
            last = lastStructure;
        }
        if (snapshot == State.IDLE || snapshot == State.AWAITING) {
            messages.send(sender, "status-idle");
            if (snapshot == State.AWAITING) {
                messages.send(sender, "await-in-progress");
            }
            return;
        }
        messages.send(sender, "status-header");
        sendStatusLine(sender, "State", snapshot.name());
        sendStatusLine(sender, "Starter", starter);
        sendStatusLine(sender, "Interval", interval + "s");
        sendStatusLine(sender, "Time left", remaining + "s (" + TimerBar.percent(remaining, interval) + "%)");
        sendStatusLine(sender, "Last structure", TimerBar.prettyStructure(last));
        sendStatusLine(sender, "Waves spawned", Integer.toString(spawns));
        sendStatusLine(sender, "Online players", Integer.toString(Bukkit.getOnlinePlayers().size()));
    }

    public void tick() {
        expireAwaiting();
        int remaining;
        int interval;
        boolean paused;
        boolean running;
        synchronized (this) {
            if (state != State.RUNNING && state != State.PAUSED) {
                return;
            }
            interval = intervalSeconds;
            paused = state == State.PAUSED;
            if (!paused) {
                remainingSeconds--;
                if (remainingSeconds <= 0) {
                    remainingSeconds = intervalSeconds;
                    remaining = 0;
                    running = true;
                } else {
                    remaining = remainingSeconds;
                    running = false;
                }
            } else {
                remaining = remainingSeconds;
                running = false;
            }
        }
        if (running) {
            spawnWave();
            synchronized (this) {
                remaining = remainingSeconds;
                interval = intervalSeconds;
            }
        }
        display.showCountdown(remaining, interval, paused);
    }

    public void handleJoin(Player player) {
        synchronized (this) {
            if (state != State.RUNNING && state != State.PAUSED) {
                return;
            }
        }
        display.showTo(player);
    }

    public void handleQuit(Player player) {
        display.hideFrom(player);
        synchronized (this) {
            if (state == State.AWAITING && player.getUniqueId().equals(awaitingPlayer)) {
                resetIdle();
            }
        }
    }

    public synchronized void cancelAwait(CommandSender actor, boolean notify) {
        if (state != State.AWAITING) {
            return;
        }
        UUID waiting = awaitingPlayer;
        resetIdle();
        if (notify && waiting != null) {
            Player player = Bukkit.getPlayer(waiting);
            if (player != null) {
                messages.send(player, "await-cancelled");
            } else if (actor != null) {
                messages.send(actor, "await-cancelled");
            }
        }
    }

    public void shutdown() {
        synchronized (this) {
            resetIdle();
        }
        display.hide();
    }

    private void spawnWave() {
        String shared = settings.sameStructureForAll() ? catalog.randomOne() : null;
        String announced = shared != null ? shared : "minecraft:random_structures";
        int placed = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!placer.eligible(player)) {
                continue;
            }
            String preferred = shared;
            PlatformScheduler.runEntitySync(player, () -> {
                String used = placer.placeOn(player, preferred);
                if (used != null) {
                    plugin.getLogger().info("Placed " + used + " on " + player.getName());
                }
            });
            placed++;
        }
        synchronized (this) {
            spawnCount++;
            lastStructure = announced;
        }
        if (placed == 0) {
            Bukkit.getOnlinePlayers().forEach(p -> messages.send(p, "spawn-failed"));
            return;
        }
        display.flashSpawn(announced);
        Map<String, String> placeholders = Map.of("structure", TimerBar.prettyStructure(announced));
        for (Player player : Bukkit.getOnlinePlayers()) {
            messages.send(player, "spawn-broadcast", placeholders);
        }
    }

    private void expireAwaiting() {
        UUID waiting = null;
        synchronized (this) {
            if (state != State.AWAITING) {
                return;
            }
            if (System.currentTimeMillis() < awaitingDeadlineMillis) {
                return;
            }
            waiting = awaitingPlayer;
            resetIdle();
        }
        if (waiting != null) {
            Player player = Bukkit.getPlayer(waiting);
            if (player != null) {
                messages.send(player, "await-timeout");
            }
        }
    }

    private void resetIdle() {
        state = State.IDLE;
        awaitingPlayer = null;
        awaitingDeadlineMillis = 0L;
        remainingSeconds = 0;
        intervalSeconds = 0;
    }

    private void broadcast(String key, Map<String, String> placeholders) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            messages.send(player, key, placeholders);
        }
        if (Bukkit.getConsoleSender() != null) {
            messages.send(Bukkit.getConsoleSender(), key, placeholders);
        }
    }

    private void sendStatusLine(CommandSender sender, String label, String value) {
        messages.send(sender, "status-line", Map.of("input", label + ":", "name", value == null ? "-" : value));
    }

    public Map<String, String> debugSnapshot() {
        synchronized (this) {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("state", state.name());
            map.put("interval", Integer.toString(intervalSeconds));
            map.put("remaining", Integer.toString(remainingSeconds));
            map.put("starter", starterName);
            map.put("spawns", Integer.toString(spawnCount));
            map.put("last", lastStructure);
            return map;
        }
    }

    public UUID starterId() {
        return starterId;
    }
}
