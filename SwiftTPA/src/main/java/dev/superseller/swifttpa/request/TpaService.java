package dev.superseller.swifttpa.request;

import dev.superseller.swifttpa.SwiftTPAPlugin;
import dev.superseller.swifttpa.config.Messages;
import dev.superseller.swifttpa.config.SwiftTpaConfig;
import dev.superseller.swifttpa.gui.RequestsGui;
import dev.superseller.swifttpa.scheduler.PlatformScheduler;
import dev.superseller.swifttpa.storage.PlayerData;
import dev.superseller.swifttpa.util.CooldownTracker;
import dev.superseller.swifttpa.util.Sounds;
import dev.superseller.swifttpa.util.TimeParser;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Orchestrates the whole teleport-request lifecycle: sending (with block /
 * toggle / cooldown / world rules), answering (by name or "latest"), cancel,
 * expiry sweeps, warmups, the Folia-safe async teleport itself, statistics and
 * the admin spy feed.
 *
 * <p>Player-facing work always runs on the player's owning region thread via
 * {@link PlatformScheduler}; the shared request queue itself is the
 * synchronized, Bukkit-free {@link RequestStore}.
 */
public final class TpaService {

    private final SwiftTPAPlugin plugin;
    private final RequestStore store = new RequestStore();
    private final CooldownTracker cooldowns = new CooldownTracker();
    private final WarmupManager warmups;
    private final Set<UUID> spies = ConcurrentHashMap.newKeySet();

    /** Set by the plugin once the GUI exists; optional so tests can skip it. */
    private RequestsGui gui;

    public TpaService(SwiftTPAPlugin plugin) {
        this.plugin = plugin;
        this.warmups = new WarmupManager(plugin);
    }

    public void setGui(RequestsGui gui) {
        this.gui = gui;
    }

    public RequestStore store() {
        return store;
    }

    public WarmupManager warmups() {
        return warmups;
    }

    private SwiftTpaConfig config() {
        return plugin.tpaConfig();
    }

    private Messages messages() {
        return plugin.messages();
    }

    // ================================================================ send

    /** Handles /tpa and /tpahere. All checks and feedback happen inside. */
    public void sendRequest(Player sender, Player target, RequestType type) {
        long now = System.currentTimeMillis();

        if (sender.getUniqueId().equals(target.getUniqueId())) {
            messages().send(sender, "error.self");
            Sounds.play(config(), sender, "error");
            return;
        }

        PlayerData targetData = plugin.storage().data(target.getUniqueId());
        if (!targetData.requestsEnabled() && !sender.hasPermission("swifttpa.bypass.toggle")) {
            messages().send(sender, "error.target-toggle", Map.of("player", target.getName()));
            Sounds.play(config(), sender, "error");
            return;
        }
        if (targetData.isBlocked(sender.getUniqueId()) && !sender.hasPermission("swifttpa.bypass.blocked")) {
            messages().send(sender, "error.blocked-by-target", Map.of("player", target.getName()));
            Sounds.play(config(), sender, "error");
            return;
        }

        if (!sender.hasPermission("swifttpa.bypass.world")) {
            World destinationWorld = type == RequestType.TPA ? target.getWorld() : sender.getWorld();
            World moverWorld = type == RequestType.TPA ? sender.getWorld() : target.getWorld();
            if (config().isWorldDisabled(destinationWorld.getName())) {
                messages().send(sender, "error.disabled-world", Map.of("world", destinationWorld.getName()));
                Sounds.play(config(), sender, "error");
                return;
            }
            if (!config().allowCrossWorld() && !destinationWorld.equals(moverWorld)) {
                messages().send(sender, "error.cross-world");
                Sounds.play(config(), sender, "error");
                return;
            }
        }

        if (!sender.hasPermission("swifttpa.bypass.cooldown")) {
            long cooldownMs = config().cooldownSeconds() * 1000L;
            long remaining = cooldowns.remainingSeconds(sender.getUniqueId(), cooldownMs, now);
            if (remaining > 0) {
                messages().send(sender, "error.cooldown", Map.of("seconds", TimeParser.format(remaining)));
                Sounds.play(config(), sender, "error");
                return;
            }
        }

        long expireSeconds = config().requestExpireSeconds();
        long expiresAt = expireSeconds <= 0 ? 0L : now + expireSeconds * 1000L;
        TeleportRequest request =
                new TeleportRequest(sender.getUniqueId(), target.getUniqueId(), type, now, expiresAt);
        RequestStore.AddResult result = store.add(request, config().oneOutgoing(), config().maxPendingPerTarget());

        cooldowns.stamp(sender.getUniqueId(), now);
        storageData(sender.getUniqueId()).addSent();
        persistAsync(sender.getUniqueId());

        if (result.replacedOutgoing() != null) {
            notifyReplaced(sender, result.replacedOutgoing());
        }
        if (result.evictedOldest() != null) {
            TeleportRequest evicted = result.evictedOldest();
            runForPlayer(evicted.sender(), player -> {
                messages().send(player, "expired-sender", Map.of("player", nameOf(evicted.target())));
                Sounds.play(config(), player, "expired");
            });
            if (!evicted.target().equals(sender.getUniqueId())) {
                runForPlayer(evicted.target(), player -> {
                    messages().send(player, "expired-target", Map.of("player", nameOf(evicted.sender())));
                    Sounds.play(config(), player, "expired");
                });
            }
            refreshGui(evicted.sender());
            refreshGui(evicted.target());
        }

        String sentKey = type == RequestType.TPA ? "request-sent-tpa" : "request-sent-here";
        messages().send(sender, sentKey, Map.of(
                "player", target.getName(),
                "seconds", expireSeconds <= 0 ? "∞" : String.valueOf(expireSeconds)));
        Sounds.play(config(), sender, "request-sent");

        String receivedKey = type == RequestType.TPA ? "request-received-tpa" : "request-received-here";
        Map<String, String> receivedPlaceholders = Map.of("player", sender.getName());
        Component chips = messages().componentNoPrefix("request-buttons", Map.of(
                "sender", sender.getName(),
                "seconds", expireSeconds <= 0 ? "∞" : String.valueOf(expireSeconds)));
        Component line = messages().component(receivedKey, receivedPlaceholders).append(chips);
        // Route to the target's own region thread — strict Folia etiquette.
        runForPlayer(target.getUniqueId(), player -> {
            player.sendMessage(line);
            Sounds.play(config(), player, "request-received");
        });

        spy("spy-send", Map.of(
                "sender", sender.getName(),
                "target", target.getName(),
                "type", type.spyName()), sender.getUniqueId(), target.getUniqueId());
        refreshGui(target.getUniqueId());
    }

    /** Notifies the old target (and the sender) that the sender's previous request was replaced. */
    private void notifyReplaced(Player sender, TeleportRequest replaced) {
        messages().send(sender, "cancelled-sender", Map.of("player", nameOf(replaced.target())));
        runForPlayer(replaced.target(), player -> {
            messages().send(player, "cancelled-target", Map.of("player", sender.getName()));
            Sounds.play(config(), player, "cancelled");
        });
        refreshGui(replaced.target());
    }

    // ================================================================ answer

    /**
     * Handles /tpaccept. With a null/blank sender name the newest incoming
     * request is accepted.
     */
    public void accept(Player target, String senderName) {
        TeleportRequest request = resolveIncoming(target, senderName);
        if (request == null) {
            sendNoRequestMessage(target, senderName);
            return;
        }
        long now = System.currentTimeMillis();
        if (request.isExpired(now)) {
            store.remove(request);
            sendExpiredPair(request);
            return;
        }
        Player sender = Bukkit.getPlayer(request.sender());
        if (sender == null) {
            store.remove(request);
            messages().send(target, "error.sender-offline");
            Sounds.play(config(), target, "error");
            refreshGui(target.getUniqueId());
            return;
        }
        store.remove(request);

        storageData(target.getUniqueId()).addAccepted();
        persistAsync(target.getUniqueId());

        messages().send(target, "accepted-target", Map.of("player", sender.getName()));
        Sounds.play(config(), target, "accepted");
        runForPlayer(request.sender(), player -> {
            messages().send(player, "accepted-sender", Map.of("player", target.getName()));
            Sounds.play(config(), player, "accepted");
        });

        spy("spy-accept", Map.of(
                "player", target.getName(),
                "sender", sender.getName(),
                "type", request.type().spyName()), target.getUniqueId(), sender.getUniqueId());

        Player mover = request.type() == RequestType.TPA ? sender : target;
        Player anchor = request.type() == RequestType.TPA ? target : sender;
        startWarmupOrTeleport(mover, anchor);
        refreshGui(target.getUniqueId());
        refreshGui(sender.getUniqueId());
    }

    /** Handles /tpdeny. With a null/blank sender name the newest request is denied. */
    public void deny(Player target, String senderName) {
        TeleportRequest request = resolveIncoming(target, senderName);
        if (request == null) {
            sendNoRequestMessage(target, senderName);
            return;
        }
        store.remove(request);

        storageData(target.getUniqueId()).addDenied();
        persistAsync(target.getUniqueId());

        runForPlayer(request.sender(), player -> {
            messages().send(player, "denied-sender", Map.of("player", target.getName()));
            Sounds.play(config(), player, "denied");
        });
        messages().send(target, "denied-target", Map.of("player", nameOf(request.sender())));
        Sounds.play(config(), target, "denied");

        spy("spy-deny", Map.of(
                "player", target.getName(),
                "sender", nameOf(request.sender()),
                "type", request.type().spyName()), target.getUniqueId(), request.sender());
        refreshGui(target.getUniqueId());
        refreshGui(request.sender());
    }

    /** Handles /tpacancel. */
    public void cancel(Player sender) {
        var outgoing = store.removeOutgoing(sender.getUniqueId());
        if (outgoing.isEmpty()) {
            messages().send(sender, "error.none-outgoing");
            Sounds.play(config(), sender, "error");
            return;
        }
        TeleportRequest request = outgoing.get();
        messages().send(sender, "cancelled-sender", Map.of("player", nameOf(request.target())));
        Sounds.play(config(), sender, "cancelled");
        runForPlayer(request.target(), player -> {
            messages().send(player, "cancelled-target", Map.of("player", sender.getName()));
            Sounds.play(config(), player, "cancelled");
        });
        spy("spy-cancel", Map.of(
                "player", sender.getName(),
                "type", request.type().spyName()), sender.getUniqueId(), request.target());
        refreshGui(sender.getUniqueId());
        refreshGui(request.target());
    }

    /** Resolves the incoming request the player wants to answer, or null. */
    private TeleportRequest resolveIncoming(Player target, String senderName) {
        if (senderName == null || senderName.isBlank()) {
            return store.latestIncoming(target.getUniqueId()).orElse(null);
        }
        for (TeleportRequest candidate : store.incoming(target.getUniqueId())) {
            Player sender = Bukkit.getPlayer(candidate.sender());
            if (sender != null && sender.getName().equalsIgnoreCase(senderName)) {
                return candidate;
            }
        }
        return null;
    }

    private void sendNoRequestMessage(Player target, String senderName) {
        if (senderName == null || senderName.isBlank()) {
            messages().send(target, "error.none");
        } else {
            messages().send(target, "error.none-from", Map.of("player", senderName));
        }
        Sounds.play(config(), target, "error");
    }

    // ================================================================ toggle / block

    /** Handles /tpatoggle. Returns the new state. */
    public boolean toggle(Player player) {
        PlayerData data = storageData(player.getUniqueId());
        boolean nowEnabled = !data.requestsEnabled();
        data.setRequestsEnabled(nowEnabled);
        persistAsync(player.getUniqueId());
        messages().send(player, nowEnabled ? "toggle-on" : "toggle-off");
        Sounds.play(config(), player, "toggle");
        refreshGui(player.getUniqueId());
        return nowEnabled;
    }

    /** Handles /tpablock {@code <player>}. */
    public void block(Player player, Player targetPlayer) {
        PlayerData data = storageData(player.getUniqueId());
        if (!data.block(targetPlayer.getUniqueId(), targetPlayer.getName())) {
            messages().send(player, "error.already-blocked", Map.of("player", targetPlayer.getName()));
            Sounds.play(config(), player, "error");
            return;
        }
        persistAsync(player.getUniqueId());
        messages().send(player, "blocked", Map.of("player", targetPlayer.getName()));
        Sounds.play(config(), player, "blocked");
    }

    /** Handles /tpaunblock {@code <player>}. */
    public void unblock(Player player, String nameOrUuid) {
        PlayerData data = storageData(player.getUniqueId());
        String removed = data.unblock(nameOrUuid);
        if (removed == null) {
            messages().send(player, "error.not-blocked", Map.of("player", nameOrUuid));
            Sounds.play(config(), player, "error");
            return;
        }
        persistAsync(player.getUniqueId());
        messages().send(player, "unblocked", Map.of("player", removed));
        Sounds.play(config(), player, "unblocked");
    }

    /** Handles /tpablock list. */
    public void sendBlockList(Player player) {
        PlayerData data = storageData(player.getUniqueId());
        List<String> names = data.blockedNames();
        if (names.isEmpty()) {
            messages().send(player, "blocklist-empty");
            return;
        }
        messages().send(player, "blocklist-header", Map.of("count", String.valueOf(names.size())));
        for (String name : names) {
            messages().send(player, "blocklist-line", Map.of("player", name));
        }
    }

    // ================================================================ list

    /** Handles /tpalist — pending incoming requests plus the outgoing one. */
    public void sendList(Player player) {
        long now = System.currentTimeMillis();
        List<TeleportRequest> incoming = store.incoming(player.getUniqueId());
        var outgoing = store.outgoing(player.getUniqueId());
        if (incoming.isEmpty() && outgoing.isEmpty()) {
            messages().send(player, "list-empty");
            return;
        }
        messages().send(player, "list-header",
                Map.of("count", String.valueOf(incoming.size() + (outgoing.isPresent() ? 1 : 0))));
        for (TeleportRequest request : incoming) {
            String key = request.type() == RequestType.TPA ? "list-incoming-tpa" : "list-incoming-here";
            messages().send(player, key, Map.of(
                    "player", nameOf(request.sender()),
                    "seconds", request.expiresAtMs() <= 0 ? "∞" : TimeParser.format(request.secondsLeft(now))));
        }
        if (outgoing.isPresent()) {
            TeleportRequest request = outgoing.get();
            String key = request.type() == RequestType.TPA ? "list-outgoing-tpa" : "list-outgoing-here";
            messages().send(player, key, Map.of(
                    "player", nameOf(request.target()),
                    "type", request.type().spyName(),
                    "seconds", request.expiresAtMs() <= 0 ? "∞" : TimeParser.format(request.secondsLeft(now))));
        }
    }

    // ================================================================ expiry / quit

    /** Periodic sweep (every second): expires stale requests and informs both sides. */
    public void expireSweep() {
        List<TeleportRequest> expired = store.expire(System.currentTimeMillis());
        for (TeleportRequest request : expired) {
            sendExpiredPair(request);
        }
    }

    private void sendExpiredPair(TeleportRequest request) {
        runForPlayer(request.sender(), player -> {
            messages().send(player, "expired-sender", Map.of("player", nameOf(request.target())));
            Sounds.play(config(), player, "expired");
        });
        runForPlayer(request.target(), player -> {
            messages().send(player, "expired-target", Map.of("player", nameOf(request.sender())));
            Sounds.play(config(), player, "expired");
        });
        spy("spy-expire", Map.of(
                "sender", nameOf(request.sender()),
                "target", nameOf(request.target()),
                "type", request.type().spyName()), request.sender(), request.target());
        refreshGui(request.sender());
        refreshGui(request.target());
    }

    /** Cleans up everything involving a quitting player; the other side is informed. */
    public void onPlayerQuit(UUID uuid, String name) {
        List<TeleportRequest> removed = store.removeAllInvolving(uuid);
        for (TeleportRequest request : removed) {
            UUID other = request.sender().equals(uuid) ? request.target() : request.sender();
            runForPlayer(other, player -> {
                messages().send(player, "cancelled-target", Map.of("player", name));
                Sounds.play(config(), player, "cancelled");
            });
            refreshGui(other);
        }
        warmups.cancel(uuid, WarmupManager.CancelReason.QUIT);
        warmups.cancelWhereAnchor(uuid);
    }

    // ================================================================ teleport

    private void startWarmupOrTeleport(Player mover, Player anchor) {
        long seconds = config().warmupSeconds();
        if (seconds <= 0L || mover.hasPermission("swifttpa.bypass.warmup")) {
            executeTeleport(mover.getUniqueId(), anchor.getUniqueId());
            return;
        }
        messages().send(mover, "warmup-started", Map.of("seconds", String.valueOf(seconds)));
        warmups.start(mover, anchor.getUniqueId(), (int) seconds, this::executeTeleport);
    }

    /**
     * The actual teleport. The destination is captured on the anchor's own
     * region thread, then the mover travels via {@code teleportAsync} — the
     * officially Folia-safe way for cross-region teleports.
     */
    public void executeTeleport(UUID moverId, UUID anchorId) {
        Player mover = Bukkit.getPlayer(moverId);
        Player anchor = Bukkit.getPlayer(anchorId);
        if (mover == null) {
            return;
        }
        if (anchor == null) {
            PlatformScheduler.runForPlayer(mover, () -> {
                plugin.messages().send(mover, "error.target-offline");
                Sounds.play(plugin.tpaConfig(), mover, "error");
            });
            return;
        }
        PlatformScheduler.runForPlayer(anchor, () -> {
            Location destination = anchor.getLocation();
            if (!mover.isOnline()) {
                return;
            }
            mover.teleportAsync(destination).thenAccept(success -> {
                if (!Boolean.TRUE.equals(success)) {
                    PlatformScheduler.runForPlayer(mover, () -> {
                        plugin.messages().send(mover, "warmup-cancelled");
                        Sounds.play(plugin.tpaConfig(), mover, "error");
                    });
                    return;
                }
                String anchorName = anchor.getName();
                String moverName = mover.getName();
                PlatformScheduler.runForPlayer(mover, () -> {
                    plugin.messages().send(mover, "teleported", Map.of("player", anchorName));
                    Sounds.play(plugin.tpaConfig(), mover, "teleported");
                    plugin.storage().data(moverId).addTeleported();
                    persistAsync(moverId);
                });
                PlatformScheduler.runForPlayer(anchor, () -> {
                    plugin.messages().send(anchor, "teleported-here-side", Map.of("player", moverName));
                    Sounds.play(plugin.tpaConfig(), anchor, "teleported");
                });
                spy("spy-teleport", Map.of("player", moverName, "target", anchorName), moverId, anchorId);
                refreshGui(moverId);
                refreshGui(anchorId);
            });
        });
    }

    // ================================================================ admin

    /** Admin force-teleport: instant, skips requests, cooldowns and warmups. */
    public void forceTeleport(CommandSender admin, Player mover, Player anchor) {
        messages().send(admin, "admin.forced-tp", Map.of(
                "player", mover.getName(), "target", anchor.getName()));
        PlatformScheduler.runForPlayer(mover,
                () -> messages().send(mover, "admin.forced-notify-player"));
        executeTeleport(mover.getUniqueId(), anchor.getUniqueId());
    }

    /** Clears every pending request (admin). */
    public void clearAll(CommandSender admin) {
        int removed = store.clear();
        messages().send(admin, removed == 0 ? "admin.clear-none" : "admin.cleared-all",
                Map.of("count", String.valueOf(removed)));
        refreshAllViewers();
    }

    /** Clears every request involving one player (admin). */
    public void clearFor(CommandSender admin, UUID playerId, String playerName) {
        int removed = store.removeAllInvolving(playerId).size();
        if (removed == 0) {
            messages().send(admin, "admin.clear-none");
        } else {
            messages().send(admin, "admin.cleared-player", Map.of(
                    "player", playerName, "count", String.valueOf(removed)));
        }
        refreshAllViewers();
    }

    /** Toggles the spy feed for an admin. Returns the new state. */
    public boolean toggleSpy(Player admin) {
        boolean nowSpying;
        if (spies.remove(admin.getUniqueId())) {
            nowSpying = false;
        } else {
            spies.add(admin.getUniqueId());
            nowSpying = true;
        }
        messages().send(admin, nowSpying ? "admin.spy-on" : "admin.spy-off");
        Sounds.play(config(), admin, "toggle");
        return nowSpying;
    }

    public boolean isSpying(UUID uuid) {
        return spies.contains(uuid);
    }

    /** Sends one player's teleport statistics to a viewer. */
    public void sendStats(CommandSender viewer, PlayerData data, String name, boolean self) {
        messages().send(viewer, self ? "stats-self" : "stats-other", Map.of(
                "player", name,
                "sent", String.valueOf(data.sentCount()),
                "accepted", String.valueOf(data.acceptedCount()),
                "denied", String.valueOf(data.deniedCount()),
                "teleported", String.valueOf(data.teleportedCount())));
    }

    /** Version / platform / storage summary for /stpa info and /stpaadmin info. */
    public void sendInfo(CommandSender viewer) {
        messages().send(viewer, "info-line", Map.of(
                "version", plugin.getDescription().getVersion(),
                "platform", PlatformScheduler.isFolia() ? "Folia" : "Paper/Purpur",
                "storage", config().storageType()));
    }

    // ================================================================ helpers

    private void spy(String key, Map<String, String> placeholders, UUID... involved) {
        if (!config().spyEnabled() || spies.isEmpty()) {
            return;
        }
        List<UUID> excluded = List.of(involved);
        for (UUID spyId : spies) {
            if (excluded.contains(spyId)) {
                continue;
            }
            runForPlayer(spyId, player -> {
                if (player.hasPermission("swifttpa.admin")) {
                    messages().send(player, key, placeholders);
                }
            });
        }
    }

    private PlayerData storageData(UUID uuid) {
        return plugin.storage().data(uuid);
    }

    private void persistAsync(UUID uuid) {
        PlatformScheduler.runAsync(() -> plugin.storage().savePlayer(uuid));
    }

    private void runForPlayer(UUID uuid, java.util.function.Consumer<Player> action) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        PlatformScheduler.runForPlayer(player, () -> action.accept(player));
    }

    /** Best-effort display name for a uuid that may be offline. */
    public String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }

    private void refreshGui(UUID uuid) {
        if (gui != null) {
            gui.refresh(uuid);
        }
    }

    private void refreshAllViewers() {
        if (gui != null) {
            gui.refreshAll();
        }
    }
}
