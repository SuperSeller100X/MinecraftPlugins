package dev.superseller.swifttpa.request;

import dev.superseller.swifttpa.SwiftTPAPlugin;
import dev.superseller.swifttpa.scheduler.PlatformScheduler;
import dev.superseller.swifttpa.util.Sounds;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.bukkit.entity.Player;

/**
 * Delayed teleports with a live countdown. A warmup starts when a request is
 * accepted (unless the mover has the bypass permission) and completes on the
 * mover's own region thread, which makes it fully Folia-safe. Movement,
 * damage, logout or a vanished anchor cancel the warmup.
 */
public final class WarmupManager {

    /** Why a warmup ended early. */
    public enum CancelReason {
        MOVED,
        DAMAGE,
        QUIT,
        OTHER
    }

    /** Mutable countdown state for one teleport. */
    private static final class WarmupSession {
        private final UUID anchor;
        private final int initialSeconds;
        private final BiConsumer<UUID, UUID> onComplete;
        private int secondsLeft;
        private PlatformScheduler.Cancellable handle;
        private boolean done;

        private WarmupSession(UUID anchor, int seconds, BiConsumer<UUID, UUID> onComplete) {
            this.anchor = anchor;
            this.initialSeconds = seconds;
            this.secondsLeft = seconds;
            this.onComplete = onComplete;
        }
    }

    private final SwiftTPAPlugin plugin;
    private final Map<UUID, WarmupSession> sessions = new ConcurrentHashMap<>();

    public WarmupManager(SwiftTPAPlugin plugin) {
        this.plugin = plugin;
    }

    /** True while the player is counting down towards a teleport. */
    public boolean isWarmingUp(UUID player) {
        return sessions.containsKey(player);
    }

    /** Number of active warmups (admin info). */
    public int activeCount() {
        return sessions.size();
    }

    /**
     * Starts a countdown for a player. When the countdown finishes,
     * {@code onComplete} runs on the mover's region thread with
     * (moverId, anchorId).
     */
    public void start(Player mover, UUID anchor, int seconds, BiConsumer<UUID, UUID> onComplete) {
        UUID moverId = mover.getUniqueId();
        cancelQuiet(moverId);
        WarmupSession session = new WarmupSession(anchor, Math.max(1, seconds), onComplete);
        sessions.put(moverId, session);
        session.handle = PlatformScheduler.runPlayerTimer(mover, () -> tick(moverId), 20L, 20L);
    }

    private void tick(UUID moverId) {
        WarmupSession session = sessions.get(moverId);
        if (session == null || session.done) {
            return;
        }
        Player mover = plugin.getServer().getPlayer(moverId);
        if (mover == null || !mover.isOnline()) {
            cancelQuiet(moverId);
            return;
        }
        session.secondsLeft--;
        if (session.secondsLeft <= 0) {
            finish(moverId, session);
            return;
        }
        if (plugin.tpaConfig().actionBarCountdown()) {
            mover.sendActionBar(plugin.messages().componentNoPrefix("warmup-actionbar",
                    Map.of("seconds", String.valueOf(session.secondsLeft))));
        }
        float pitch = 1.0f + 0.15f * (session.initialSeconds - session.secondsLeft);
        Sounds.play(plugin.tpaConfig(), mover, "warmup-tick", pitch);
    }

    private void finish(UUID moverId, WarmupSession session) {
        session.done = true;
        sessions.remove(moverId);
        if (session.handle != null) {
            session.handle.cancel();
        }
        Sounds.play(plugin.tpaConfig(), plugin.getServer().getPlayer(moverId), "warmup-tick", 1.8f);
        session.onComplete.accept(moverId, session.anchor);
    }

    /** Cancels the warmup with the matching user-facing message. */
    public void cancel(UUID moverId, CancelReason reason) {
        WarmupSession session = sessions.remove(moverId);
        if (session == null) {
            return;
        }
        session.done = true;
        if (session.handle != null) {
            session.handle.cancel();
        }
        String key = switch (reason) {
            case MOVED -> "warmup-cancelled-moved";
            case DAMAGE -> "warmup-cancelled-damage";
            case QUIT, OTHER -> "warmup-cancelled";
        };
        Player mover = plugin.getServer().getPlayer(moverId);
        if (mover != null && mover.isOnline()) {
            PlatformScheduler.runForPlayer(mover, () -> {
                plugin.messages().send(mover, key);
                Sounds.play(plugin.tpaConfig(), mover, "error");
            });
        }
    }

    /** Cancels every warmup whose destination anchor is the given player. */
    public void cancelWhereAnchor(UUID anchorId) {
        for (Map.Entry<UUID, WarmupSession> entry : sessions.entrySet()) {
            if (entry.getValue().anchor.equals(anchorId)) {
                cancel(entry.getKey(), CancelReason.OTHER);
            }
        }
    }

    /** Silent cancel — used internally and when a new warmup replaces an old one. */
    public void cancelQuiet(UUID moverId) {
        WarmupSession session = sessions.remove(moverId);
        if (session != null) {
            session.done = true;
            if (session.handle != null) {
                session.handle.cancel();
            }
        }
    }

    /** Cancels everything (plugin disable). */
    public void cancelAll() {
        for (UUID moverId : sessions.keySet()) {
            cancelQuiet(moverId);
        }
        sessions.clear();
    }
}
