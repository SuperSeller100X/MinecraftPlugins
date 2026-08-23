package dev.superseller.teleportsigns.service;

import dev.superseller.teleportsigns.TeleportSignsPlugin;
import dev.superseller.teleportsigns.command.Permissions;
import dev.superseller.teleportsigns.config.PluginSettings;
import dev.superseller.teleportsigns.model.TeleportSign;
import dev.superseller.teleportsigns.model.WarpDestination;
import dev.superseller.teleportsigns.scheduler.PlatformScheduler;
import dev.superseller.teleportsigns.util.Numbers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** Cooldown, warmup, safety, cost and Folia-safe teleportAsync. */
public final class TeleportService {

    private final TeleportSignsPlugin plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<UUID, Pending>();

    public TeleportService(TeleportSignsPlugin plugin) {
        this.plugin = plugin;
    }

    public void request(Player player, TeleportSign sign) {
        if (player == null || sign == null) {
            return;
        }
        PluginSettings settings = plugin.settings();

        if (!Permissions.has(player, Permissions.USE) && !player.hasPermission(Permissions.USE)) {
            plugin.messages().send(player, "no-permission");
            return;
        }

        Pending existing = pending.get(player.getUniqueId());
        if (existing != null && !existing.cancelled) {
            plugin.messages().send(player, "already-warming");
            return;
        }

        if (!Permissions.has(player, Permissions.BYPASS_COOLDOWN)) {
            Long until = cooldowns.get(player.getUniqueId());
            long now = System.currentTimeMillis();
            if (until != null && until.longValue() > now) {
                double left = (until.longValue() - now) / 1000.0d;
                plugin.messages().send(player, "cooldown",
                        Map.of("seconds", Numbers.pretty(Math.max(0.1d, left))));
                return;
            }
        }

        WarpDestination dest = sign.destination();
        World world = Bukkit.getWorld(dest.worldName());
        if (world == null) {
            plugin.messages().send(player, "invalid-world", Map.of("world", dest.worldName()));
            return;
        }

        double cost = effectiveCost(player, sign);
        if (cost > 0.0d) {
            if (!plugin.economy().isEnabled()) {
                if (settings.denyWhenEconomyMissing()) {
                    plugin.messages().send(player, "economy-missing");
                    return;
                }
            } else if (!plugin.economy().has(player.getUniqueId(), cost)) {
                plugin.messages().send(player, "cannot-afford",
                        Map.of("cost", plugin.economy().format(cost)));
                return;
            }
        }

        long warmupTicks = Permissions.has(player, Permissions.BYPASS_WARMUP) ? 0L : settings.warmupTicks();
        Location origin = player.getLocation().clone();
        Pending wait = new Pending(sign, origin, warmupTicks);
        pending.put(player.getUniqueId(), wait);

        if (warmupTicks <= 0L) {
            finish(player, wait);
            return;
        }

        plugin.messages().send(player, "warmup",
                Map.of("seconds", Numbers.pretty(settings.warmupSeconds())));
        PlatformScheduler.runEntityDelayed(player, () -> finish(player, wait), warmupTicks);
    }

    public void cancelMove(Player player) {
        if (player == null || !plugin.settings().cancelOnMove()) {
            return;
        }
        Pending wait = pending.get(player.getUniqueId());
        if (wait == null || wait.cancelled || wait.warmupTicks <= 0L) {
            return;
        }
        Location now = player.getLocation();
        if (sameBlock(wait.origin, now)) {
            return;
        }
        wait.cancelled = true;
        pending.remove(player.getUniqueId());
        plugin.messages().send(player, "warmup-cancelled-move");
    }

    public void cancelDamage(Player player) {
        if (player == null || !plugin.settings().cancelOnDamage()) {
            return;
        }
        Pending wait = pending.remove(player.getUniqueId());
        if (wait == null || wait.cancelled) {
            return;
        }
        wait.cancelled = true;
        plugin.messages().send(player, "warmup-cancelled-damage");
    }

    public void clear(Player player) {
        if (player != null) {
            Pending wait = pending.remove(player.getUniqueId());
            if (wait != null) {
                wait.cancelled = true;
            }
        }
    }

    private void finish(Player player, Pending wait) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Pending current = pending.get(player.getUniqueId());
        if (current != wait || wait.cancelled) {
            return;
        }
        pending.remove(player.getUniqueId());

        WarpDestination dest = wait.sign.destination();
        World world = Bukkit.getWorld(dest.worldName());
        if (world == null) {
            plugin.messages().send(player, "invalid-world", Map.of("world", dest.worldName()));
            return;
        }

        Location target = new Location(world, dest.x(), dest.y(), dest.z());
        if (dest.hasRotation()) {
            target.setYaw(dest.yaw());
            target.setPitch(dest.pitch());
        } else {
            Location currentLoc = player.getLocation();
            target.setYaw(currentLoc.getYaw());
            target.setPitch(currentLoc.getPitch());
        }

        PlatformScheduler.runRegion(target, () -> completeOnRegion(player, wait.sign, target));
    }

    private void completeOnRegion(Player player, TeleportSign sign, Location target) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PluginSettings settings = plugin.settings();
        if (settings.safetyEnabled() && !Permissions.has(player, Permissions.BYPASS_SAFETY)) {
            String reason = inspect(target, settings);
            if (reason != null) {
                String label = plugin.messages().raw("unsafe-" + reason, reason);
                PlatformScheduler.runEntity(player, () -> plugin.messages().send(player, "unsafe",
                        Map.of("reason", label)));
                return;
            }
        }

        double cost = effectiveCost(player, sign);
        boolean charged = false;
        if (cost > 0.0d && plugin.economy().isEnabled()) {
            if (!plugin.economy().withdraw(player.getUniqueId(), cost)) {
                PlatformScheduler.runEntity(player, () -> plugin.messages().send(player, "cannot-afford",
                        Map.of("cost", plugin.economy().format(cost))));
                return;
            }
            charged = true;
        }

        final boolean didCharge = charged;
        final double chargedAmount = cost;
        try {
            player.teleportAsync(target).whenComplete((success, error) -> {
                boolean ok = error == null && Boolean.TRUE.equals(success);
                if (!ok) {
                    if (didCharge) {
                        plugin.economy().deposit(player.getUniqueId(), chargedAmount);
                    }
                    PlatformScheduler.runEntity(player, () -> plugin.messages().send(player, "teleport-failed"));
                    return;
                }
                afterSuccess(player, sign, target, didCharge, chargedAmount);
            });
        } catch (Throwable t) {
            // Very old fallback — should not run on Paper 26.2 / Folia.
            boolean ok = player.teleport(target);
            if (!ok) {
                if (didCharge) {
                    plugin.economy().deposit(player.getUniqueId(), chargedAmount);
                }
                PlatformScheduler.runEntity(player, () -> plugin.messages().send(player, "teleport-failed"));
                return;
            }
            afterSuccess(player, sign, target, didCharge, chargedAmount);
        }
    }

    private void afterSuccess(Player player, TeleportSign sign, Location target,
                              boolean charged, double cost) {
        if (!Permissions.has(player, Permissions.BYPASS_COOLDOWN)) {
            long cd = plugin.settings().cooldownMillis();
            if (cd > 0L) {
                cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + cd);
            }
        }
        PlatformScheduler.runEntity(player, () -> {
            WarpDestination dest = sign.destination();
            plugin.messages().send(player, "teleported", destPlaceholders(dest));
            if (charged && cost > 0.0d) {
                plugin.messages().send(player, "cost-charged",
                        Map.of("cost", plugin.economy().format(cost)));
            }
            Sound sound = plugin.settings().sound();
            if (sound != null) {
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            }
            Particle particle = plugin.settings().particle();
            if (particle != null) {
                player.spawnParticle(particle, player.getLocation().add(0.0d, 1.0d, 0.0d), 24, 0.3d, 0.6d, 0.3d, 0.02d);
            }
        });
    }

    public static Map<String, String> destPlaceholders(WarpDestination dest) {
        return Map.of(
                "world", dest.worldName(),
                "x", Numbers.prettyCoord(dest.x()),
                "y", Numbers.prettyCoord(dest.y()),
                "z", Numbers.prettyCoord(dest.z()),
                "yaw", Numbers.pretty(dest.yaw()),
                "pitch", Numbers.pretty(dest.pitch()),
                "rotation", dest.rotationSuffix()
        );
    }

    private double effectiveCost(Player player, TeleportSign sign) {
        if (Permissions.has(player, Permissions.BYPASS_COST)) {
            return 0.0d;
        }
        double cost = sign.cost();
        if (cost < 0.0d) {
            cost = plugin.settings().defaultCost();
        }
        return Math.max(0.0d, cost);
    }

    private String inspect(Location target, PluginSettings settings) {
        World world = target.getWorld();
        if (world == null) {
            return "void";
        }
        int blockX = target.getBlockX();
        int blockY = target.getBlockY();
        int blockZ = target.getBlockZ();
        int min = world.getMinHeight();
        int max = world.getMaxHeight();
        boolean inWorld = blockY >= min && blockY < max - 1;
        String feet = materialName(world, blockX, blockY, blockZ);
        String head = materialName(world, blockX, blockY + 1, blockZ);
        String below = materialName(world, blockX, blockY - 1, blockZ);
        boolean floor = false;
        if (settings.requireSolidBelow()) {
            int from = blockY - 1;
            int to = Math.max(min, blockY - settings.maxFall());
            for (int y = from; y >= to; y--) {
                Block block = world.getBlockAt(blockX, y, blockZ);
                if (block.getType().isSolid()) {
                    floor = true;
                    below = block.getType().name();
                    break;
                }
            }
        }
        return SafetyChecker.evaluate(feet, head, below, inWorld, floor,
                settings.checkLava(), settings.checkFire(),
                settings.requireSolidBelow(), settings.rejectVoid());
    }

    private static String materialName(World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return "AIR";
        }
        Material type = world.getBlockAt(x, y, z).getType();
        return type == null ? "AIR" : type.name();
    }

    private static boolean sameBlock(Location a, Location b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.getWorld() != null && b.getWorld() != null && a.getWorld() != b.getWorld()) {
            return false;
        }
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private static final class Pending {
        final TeleportSign sign;
        final Location origin;
        final long warmupTicks;
        volatile boolean cancelled;

        Pending(TeleportSign sign, Location origin, long warmupTicks) {
            this.sign = sign;
            this.origin = origin;
            this.warmupTicks = warmupTicks;
        }
    }
}
