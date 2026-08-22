package dev.superseller.chunkvoter.vote;

import dev.superseller.chunkvoter.ChunkVoterPlugin;
import dev.superseller.chunkvoter.config.Messages;
import dev.superseller.chunkvoter.scheduler.PlatformScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Owns all running chunk-regeneration votes: creation, casting, live action
 * bar updates, expiration, decision counting and (on success) the actual
 * chunk regeneration. Everything world/player-facing is scheduled through
 * {@link PlatformScheduler} so the plugin is Folia-safe.
 */
public final class VoteManager {

    private final ChunkVoterPlugin plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();

    private final Map<ChunkKey, VoteSession> active = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private volatile boolean running = false;

    public VoteManager(ChunkVoterPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        PlatformScheduler.runTimer(this::tick, 20L);
    }

    public void stop() {
        running = false;
        active.values().forEach(VoteSession::finish);
        active.clear();
        cooldowns.clear();
    }

    // ------------------------------------------------------------------
    // Public command entry points
    // ------------------------------------------------------------------

    public boolean start(Player player) {
        if (!player.hasPermission("chunkvoter.start")) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        Chunk chunk = player.getLocation().getChunk();
        World world = chunk.getWorld();
        int cx = chunk.getX();
        int cz = chunk.getZ();
        ChunkKey key = ChunkKey.of(world, cx, cz);

        if (active.containsKey(key)) {
            plugin.messages().send(player, "already-active",
                    Map.of("world", world.getName(), "x", String.valueOf(cx), "z", String.valueOf(cz)));
            return true;
        }

        long now = System.currentTimeMillis();
        long cdMillis = plugin.configuration().cooldownSeconds() * 1000L;
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && cdMillis > 0 && now - last < cdMillis) {
            long remaining = ((cdMillis - (now - last)) + 999) / 1000L;
            plugin.messages().send(player, "cooldown", Map.of("remaining", String.valueOf(remaining)));
            return true;
        }

        boolean interested = plugin.worldGuard().isProtected(world, cx, cz);
        boolean inRegion = interested && plugin.configuration().wgRequireOwner();
        Set<UUID> owners = interested ? plugin.worldGuard().owners(world, cx, cz) : Set.of();
        if (inRegion && !bypass(player) && !owners.contains(player.getUniqueId())) {
            plugin.messages().send(player, "start-denied-owner");
            return true;
        }

        long countInWorld = active.values().stream()
                .filter(s -> s.world().getName().equals(world.getName()))
                .count();
        if (countInWorld >= plugin.configuration().maxActivePerWorld()) {
            plugin.messages().send(player, "max-active");
            return true;
        }

        VoteSession session = new VoteSession(world, cx, cz, player.getUniqueId(), inRegion, owners, now,
                plugin.configuration().durationSeconds() * 1000L);
        active.put(key, session);
        cooldowns.put(player.getUniqueId(), now);

        if (plugin.configuration().announce()) {
            announce(session);
        }
        updateActionBars(session);
        return true;
    }

    public boolean castVote(Player player, boolean yes) {
        if (!player.hasPermission("chunkvoter.vote")) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        Chunk chunk = player.getLocation().getChunk();
        ChunkKey key = ChunkKey.of(chunk.getWorld(), chunk.getX(), chunk.getZ());
        VoteSession session = active.get(key);
        if (session == null) {
            plugin.messages().send(player, "no-active");
            return true;
        }
        if (session.isFinished()) {
            plugin.messages().send(player, "vote-finished");
            return true;
        }
        if (session.inRegion() && !bypass(player) && !session.regionOwners().contains(player.getUniqueId())) {
            plugin.messages().send(player, "not-allowed-here");
            return true;
        }

        UUID uid = player.getUniqueId();
        boolean had = session.hasVoted(uid);
        boolean changed = session.cast(uid, yes);
        Map<String, String> ph = Map.of("choice", yes ? "YES" : "NO");
        if (!had) {
            plugin.messages().send(player, "vote-cast", ph);
        } else if (changed) {
            plugin.messages().send(player, "already-voted", ph);
        } else {
            plugin.messages().send(player, "already-voted", ph);
        }
        updateActionBars(session);
        return true;
    }

    public boolean info(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        VoteSession session = active.get(ChunkKey.of(chunk.getWorld(), chunk.getX(), chunk.getZ()));
        if (session == null) {
            plugin.messages().send(player, "no-active");
            return true;
        }
        Map<String, String> ph = sessionPlaceholders(session, System.currentTimeMillis());
        player.sendMessage(plugin.messages().component("info-header", ph));
        player.sendMessage(plugin.messages().component("info-line", ph));
        player.sendMessage(plugin.messages().component("info-in-region",
                Map.of("value", session.inRegion() ? "owner only" : "everyone")));
        return true;
    }

    public void adminList(CommandSender sender) {
        if (active.isEmpty()) {
            plugin.messages().send(sender, "admin-none");
            return;
        }
        sender.sendMessage(plugin.messages().component("admin-list-header",
                Map.of("count", String.valueOf(active.size()))));
        long now = System.currentTimeMillis();
        for (VoteSession s : active.values()) {
            sender.sendMessage(mini.deserialize(Messages.apply(
                    plugin.messages().raw("admin-list-line"), sessionPlaceholders(s, now))));
        }
    }

    public void adminInfo(CommandSender sender) {
        sender.sendMessage(plugin.messages().component("admin-info", Map.of(
                "duration", String.valueOf(plugin.configuration().durationSeconds()),
                "cooldown", String.valueOf(plugin.configuration().cooldownSeconds()),
                "max", String.valueOf(plugin.configuration().maxActivePerWorld()),
                "worldguard", plugin.worldGuard().describe())));
    }

    public boolean cancel(ChunkKey key) {
        VoteSession s = active.remove(key);
        if (s == null) {
            return false;
        }
        s.finish();
        broadcastMessage(s.world().getName(), plugin.messages().component("vote-cancelled",
                Map.of("world", s.world().getName(), "x", String.valueOf(s.chunkX()), "z", String.valueOf(s.chunkZ()))));
        return true;
    }

    public int cancelAll() {
        int count = 0;
        List<ChunkKey> keys = new ArrayList<>(active.keySet());
        for (ChunkKey key : keys) {
            if (cancel(key)) {
                count++;
            }
        }
        return count;
    }

    /** Cancels any vote started by the given player; returns how many were cancelled. */
    public int cancelForInitiator(UUID initiator) {
        List<ChunkKey> keys = new ArrayList<>();
        for (Map.Entry<ChunkKey, VoteSession> e : active.entrySet()) {
            if (initiator.equals(e.getValue().initiator())) {
                keys.add(e.getKey());
            }
        }
        int count = 0;
        for (ChunkKey key : keys) {
            VoteSession s = active.get(key);
            if (s != null) {
                s.finish();
                active.remove(key);
                broadcastMessage(s.world().getName(), plugin.messages().component("vote-cancelled-init",
                        Map.of("world", s.world().getName(), "x", String.valueOf(s.chunkX()), "z", String.valueOf(s.chunkZ()))));
                count++;
            }
        }
        return count;
    }

    /** Force-regenerates a chunk, cancelling an active vote if there is one. */
    public void force(World world, int x, int z) {
        ChunkKey key = ChunkKey.of(world, x, z);
        VoteSession s = active.remove(key);
        if (s != null) {
            s.finish();
        }
        Map<String, String> ph = Map.of("world", world.getName(), "x", String.valueOf(x), "z", String.valueOf(z));
        broadcastMessage(world.getName(), plugin.messages().component("admin-forced", ph));
        regenerate(world, x, z, ph);
    }

    public boolean isActive(World world, int x, int z) {
        return active.containsKey(ChunkKey.of(world, x, z));
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private boolean bypass(Player player) {
        return player.hasPermission("chunkvoter.bypass")
                || (plugin.configuration().wgAdminsBypass() && player.hasPermission("chunkvoter.admin"));
    }

    private void announce(VoteSession s) {
        Map<String, String> ph = sessionPlaceholders(s, System.currentTimeMillis());
        Component line = plugin.messages().component("vote-started", ph)
                .append(Component.space())
                .append(button("vote-button-yes", "/chunkvoter yes"))
                .append(Component.space())
                .append(button("vote-button-no", "/chunkvoter no"));
        broadcastMessage(s.world().getName(), line);
    }

    private Component button(String key, String command) {
        return mini.deserialize(plugin.messages().raw(key))
                .clickEvent(ClickEvent.suggestCommand(command));
    }

    private void tick() {
        if (!running) {
            return;
        }
        long now = System.currentTimeMillis();
        List<VoteSession> resolved = new ArrayList<>();
        for (VoteSession s : active.values()) {
            if (s.isFinished()) {
                resolved.add(s);
                continue;
            }
            if (now >= s.endAt()) {
                s.finish();
                resolve(s);
                resolved.add(s);
            } else {
                updateActionBars(s);
            }
        }
        for (VoteSession s : resolved) {
            active.remove(s.key());
        }
    }

    private void resolve(VoteSession s) {
        int yes = s.yes();
        int no = s.no();
        Map<String, String> ph = sessionPlaceholders(s, System.currentTimeMillis());
        if (yes > no) {
            broadcastMessage(s.world().getName(), plugin.messages().component("result-yes", ph));
            regenerate(s.world(), s.chunkX(), s.chunkZ(), ph);
        } else if (no > yes) {
            broadcastMessage(s.world().getName(), plugin.messages().component("result-no", ph));
        } else {
            broadcastMessage(s.world().getName(), plugin.messages().component("result-tie", ph));
        }
    }

    private void regenerate(World world, int cx, int cz, Map<String, String> ph) {
        PlatformScheduler.runRegionSync(world, cx, cz, () -> {
            boolean ok = false;
            Throwable err = null;
            try {
                if (plugin.configuration().requireChunkLoad() && !world.isChunkLoaded(cx, cz)) {
                    world.loadChunk(cx, cz, false);
                }
                ok = world.regenerateChunk(cx, cz);
            } catch (Throwable t) {
                err = t;
            }
            if (err instanceof UnsupportedOperationException) {
                broadcastMessage(world.getName(), plugin.messages().component("regen-unsupported", ph));
            } else if (ok) {
                broadcastMessage(world.getName(), plugin.messages().component("regen-success", ph));
            } else {
                plugin.getLogger().warning("Failed to regenerate chunk " + world.getName()
                        + " (" + cx + ", " + cz + ")" + (err == null ? "" : ": " + err.getMessage()));
                broadcastMessage(world.getName(), plugin.messages().component("regen-failed", ph));
            }
        });
    }

    private void updateActionBars(VoteSession s) {
        Map<String, String> ph = sessionPlaceholders(s, System.currentTimeMillis());
        Component bar = mini.deserialize(Messages.apply(plugin.messages().raw("actionbar"), ph));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getName().equals(s.world().getName())) {
                PlatformScheduler.runEntitySync(p, () -> p.sendActionBar(bar));
            }
        }
    }

    private void broadcastMessage(String worldName, Component message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getName().equals(worldName)) {
                PlatformScheduler.runEntitySync(p, () -> p.sendMessage(message));
            }
        }
    }

    private Map<String, String> sessionPlaceholders(VoteSession s, long now) {
        long remaining = s.remainingMillis(now);
        long seconds = (remaining + 999) / 1000L;
        String initiator = nilToDash(Bukkit.getOfflinePlayer(s.initiator()).getName());
        return Map.of(
                "world", s.world().getName(),
                "x", String.valueOf(s.chunkX()),
                "z", String.valueOf(s.chunkZ()),
                "yes", String.valueOf(s.yes()),
                "no", String.valueOf(s.no()),
                "total", String.valueOf(s.total()),
                "time", String.valueOf(seconds),
                "initiator", initiator);
    }

    private static String nilToDash(String value) {
        return value == null ? "-" : value;
    }
}
