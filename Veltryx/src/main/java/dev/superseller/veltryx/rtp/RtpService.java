package dev.superseller.veltryx.rtp;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.util.PlatformScheduler;

/**
 * Random teleportation: picks a random spot in a 10 million block radius
 * (configurable), searches for a safe block column, counts down 3 seconds
 * and teleports. The search runs asynchronously; the countdown and the
 * teleport happen on the player's scheduler (Folia-safe).
 */
public final class RtpService {

    private final VeltryxConfig config;
    private final Messages messages;
    /** Next allowed /rtp moment per player. */
    private final Map<UUID, Long> nextAllowed = new ConcurrentHashMap<>();

    public RtpService(VeltryxConfig config, Messages messages) {
        this.config = config;
        this.messages = messages;
    }

    /** Whether the player is still in the cooldown, in seconds (0 = ready). */
    public long cooldownLeft(Player player) {
        long readyAt = nextAllowed.getOrDefault(player.getUniqueId(), 0L);
        return Math.max(0L, (readyAt - System.currentTimeMillis() + 999) / 1000L);
    }

    /**
     * Starts an RTP run for the given world kind ("overworld", "nether",
     * "end"). Starts the cooldown, searches asynchronously, then counts
     * down and teleports.
     */
    public void start(Player player, String worldKind) {
        long now = System.currentTimeMillis();
        if (now < nextAllowed.getOrDefault(player.getUniqueId(), 0L)) {
            messages.send(player, "rtp.cooldown",
                    Map.of("seconds", String.valueOf(cooldownLeft(player))));
            return;
        }
        World world = pickWorld(worldKind);
        if (world == null) {
            messages.send(player, "rtp.world-missing", Map.of("world", worldKind));
            return;
        }
        nextAllowed.put(player.getUniqueId(), now + config.rtpCooldownSeconds() * 1000L);
        messages.send(player, "rtp.searching", Map.of("world", worldKind));
        PlatformScheduler.runAsync(() -> {
            Location spot = findSafeSpot(world);
            if (spot == null) {
                messages.send(player, "rtp.failed", Map.of());
                return;
            }
            PlatformScheduler.runForPlayer(player, () -> countdown(player, spot));
        });
    }

    /** The world for a kind, or {@code null} when it is not loaded. */
    private World pickWorld(String worldKind) {
        for (World world : org.bukkit.Bukkit.getWorlds()) {
            World.Environment env = world.getEnvironment();
            switch (worldKind) {
                case "overworld" -> {
                    if (env == World.Environment.NORMAL) {
                        return world;
                    }
                }
                case "nether" -> {
                    if (env == World.Environment.NETHER) {
                        return world;
                    }
                }
                case "end" -> {
                    if (env == World.Environment.THE_END) {
                        return world;
                    }
                }
                default -> { }
            }
        }
        return null;
    }

    /** Finds a safe teleport spot in the world, or {@code null} after the attempts run out. */
    Location findSafeSpot(World world) {
        boolean nether = world.getEnvironment() == World.Environment.NETHER;
        long radius = config.rtpRadius() / (nether ? Math.max(1L, config.rtpNetherDivisor()) : 1L);
        long minDistance = config.rtpMinDistance();
        int attempts = config.rtpMaxAttempts();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < attempts; attempt++) {
            long range = Math.max(1L, radius * 2);
            long x = (random.nextLong(range)) - radius;
            long z = (random.nextLong(range)) - radius;
            if (Math.abs(x) < minDistance && Math.abs(z) < minDistance) {
                continue;   // too close to spawn
            }
            Location found = scanColumn(world, (int) Math.max(Integer.MIN_VALUE + 2, Math.min(Integer.MAX_VALUE - 2, x)),
                    (int) Math.max(Integer.MIN_VALUE + 2, Math.min(Integer.MAX_VALUE - 2, z)), nether);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Scans one column top-down for a safe standing spot. */
    private Location scanColumn(World world, int x, int z, boolean nether) {
        int top = world.getHighestBlockYAt(x, z);
        int startY = Math.min(top + 1, world.getMaxHeight() - 2);
        if (nether) {
            // never start inside or above the bedrock roof
            startY = Math.min(startY, 110);
        }
        for (int y = startY; y > world.getMinHeight() + 1; y--) {
            Block ground = world.getBlockAt(x, y - 1, z);
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);
            if (RtpSafety.isSafe(ground.getType().name(), ground.getType().isSolid(),
                    feet.getType().name(), head.getType().name())) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return null;
    }

    /** 3 second countdown on the action bar, then the teleport. */
    private void countdown(Player player, Location spot) {
        countdownTick(player, spot, config.rtpCountdownSeconds());
    }

    private void countdownTick(Player player, Location spot, long seconds) {
        if (seconds <= 0) {
            player.teleportAsync(spot);
            messages.send(player, "rtp.teleported", Map.of(
                    "world", spot.getWorld() == null ? "world" : spot.getWorld().getName(),
                    "x", String.valueOf((int) spot.getX()),
                    "y", String.valueOf((int) spot.getY()),
                    "z", String.valueOf((int) spot.getZ())));
            return;
        }
        player.sendActionBar(messages.render("rtp.countdown", Map.of("seconds", String.valueOf(seconds))));
        PlatformScheduler.runLater(() -> countdownTick(player, spot, seconds - 1), 20L);
    }
}
