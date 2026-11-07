package dev.superseller.veltryx.anticheat.listener;

import dev.superseller.veltryx.anticheat.AntiCheatService;
import dev.superseller.veltryx.anticheat.CheckType;
import dev.superseller.veltryx.anticheat.PingGuard;
import dev.superseller.veltryx.anticheat.PlayerTrace;
import dev.superseller.veltryx.anticheat.XrayStats;
import dev.superseller.veltryx.anticheat.xray.OreObfuscator;
import dev.superseller.veltryx.config.VeltryxConfig;
import java.util.List;
import java.util.Locale;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Server-side anti-cheat detections: movement (fly, speed, jesus, phase,
 * timer), combat (reach, autoclicker, multi-aura, aim) and interaction
 * (fast place, fast use, interact reach), plus the X-Ray mining statistics.
 *
 * <p>Every check is conservative and ping/TPS aware; see
 * {@link AntiCheatService#flag} for the escalation pipeline.
 */
public final class AntiCheatListener implements Listener {

    private static final Material[] CLIMBING = {
            Material.LADDER, Material.VINE, Material.SCAFFOLDING,
            Material.TWISTING_VINES, Material.WEEPING_VINES
    };

    private final AntiCheatService service;
    private final VeltryxConfig config;
    private final OreObfuscator obfuscator;

    public AntiCheatListener(AntiCheatService service, VeltryxConfig config, OreObfuscator obfuscator) {
        this.service = service;
        this.config = config;
        this.obfuscator = obfuscator;
    }

    // ------------------------------------------------------------ lifecycle

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        service.startTracking(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.stopTracking(event.getPlayer().getUniqueId());
        obfuscator.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (checked(player)) {
            service.trace(player).skipNextMove();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (checked(player)) {
            service.trace(player).skipNextMove();
        }
    }

    // ----------------------------------------------------------- movement

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!checked(player)) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return;
        }

        PlayerTrace trace = service.trace(player);
        long nowNanos = System.nanoTime();
        long nowMillis = System.currentTimeMillis();

        if (!trace.positionInitialized() || trace.consumeSkipNextMove()) {
            trace.recordPosition(to.getX(), to.getY(), to.getZ(), nowNanos);
            trace.pushMoveTime(nowMillis);
            obfuscator.scanAround(player);
            return;
        }

        int ping = player.getPing();
        double tps = AntiCheatService.currentTps();
        if (PingGuard.shouldSkip(ping, tps, config.maxPing(), config.minTps())) {
            trace.recordPosition(to.getX(), to.getY(), to.getZ(), nowNanos);
            return;
        }
        if (nowMillis - trace.joinedAtMillis() < config.joinGraceSeconds() * 1000L) {
            trace.recordPosition(to.getX(), to.getY(), to.getZ(), nowNanos);
            return;
        }

        double factor = PingGuard.allowanceFactor(ping, tps);
        double dx = to.getX() - trace.lastX();
        double dy = to.getY() - trace.lastY();
        double dz = to.getZ() - trace.lastZ();
        double seconds = trace.secondsSinceLastMove(nowNanos);
        trace.touchMove(nowNanos);
        trace.recordPosition(to.getX(), to.getY(), to.getZ(), nowNanos);
        trace.pushMoveTime(nowMillis);

        boolean skipPhysics = player.isInsideVehicle() || player.isGliding() || player.isFlying()
                || player.isDead() || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR || player.isRiptiding();

        // Timer: far more position updates than the 20 TPS game loop produces.
        if (trace.moveEventsLastSecond() > (int) (22.0 * factor) + 4) {
            service.flag(player, CheckType.TIMER, 1.0,
                    trace.moveEventsLastSecond() + " moves/s");
        }

        if (skipPhysics) {
            return;
        }

        boolean climbing = isClimbing(to.getBlock().getType()) || isClimbing(from.getBlock().getType());
        Material feet = to.getBlock().getType();
        boolean inLiquid = feet == Material.WATER || feet == Material.LAVA;

        // Jesus: skating across the water surface at walking speed.
        if (inLiquid && !player.isSwimming() && !climbing && seconds > 0.02) {
            double horizontal = Math.sqrt(dx * dx + dz * dz) / seconds;
            if (horizontal > 2.8 * factor) {
                service.flag(player, CheckType.JESUS, 1.2,
                        String.format(Locale.ROOT, "%.1f b/s on water", horizontal));
            }
        }

        // Phase: moving while fully inside solid blocks.
        if (!inLiquid && !climbing
                && from.getBlock().getType().isSolid() && to.getBlock().getType().isSolid()) {
            service.flag(player, CheckType.PHASE, 3.0,
                    from.getBlock().getType() + " -> " + to.getBlock().getType());
            return;
        }

        if (climbing || inLiquid) {
            return;
        }

        boolean onGround = to.clone().subtract(0, 0.1, 0).getBlock().getType().isSolid();

        // Speed: horizontal distance per second above any legitimate baseline.
        if (seconds > 0.02 && seconds < 2.0) {
            double horizontal = Math.sqrt(dx * dx + dz * dz) / seconds;
            double limit = config.maxMoveSpeed() * factor * situationalSpeedMultiplier(player, to);
            if (horizontal > limit) {
                service.flag(player, CheckType.SPEED, 1.0 + Math.min(3.0, (horizontal - limit) / 2.0),
                        String.format(Locale.ROOT, "%.1f b/s (limit %.1f)", horizontal, limit));
            }
        }

        // Fly: staying airborne far longer than any jump, without falling.
        if (onGround || dy < -0.08) {
            trace.setAirTicks(0);
        } else {
            trace.setAirTicks(trace.airTicks() + 1);
            double maxAirSeconds = 1.1 * factor;
            double airborneSeconds = trace.airTicks() * 0.05;
            if (airborneSeconds > maxAirSeconds && dy >= -0.03) {
                service.flag(player, CheckType.FLY,
                        1.0 + Math.min(2.0, (airborneSeconds - maxAirSeconds)),
                        String.format(Locale.ROOT, "hovering for %.1fs (dy=%.2f)", airborneSeconds, dy));
            }
        }

        obfuscator.scanAround(player);
    }

    // ------------------------------------------------------------- combat

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (!checked(attacker)) {
            return;
        }
        PlayerTrace trace = service.trace(attacker);
        long now = System.currentTimeMillis();
        if (now - trace.joinedAtMillis() < config.joinGraceSeconds() * 1000L) {
            return;
        }
        int ping = attacker.getPing();
        double tps = AntiCheatService.currentTps();
        if (PingGuard.shouldSkip(ping, tps, config.maxPing(), config.minTps())) {
            return;
        }
        double factor = PingGuard.allowanceFactor(ping, tps);

        // Auto-clicker: more attacks per second than humanly possible.
        int attacksPerSecond = trace.pushAttack(now);
        if (attacksPerSecond > config.maxCps() * factor) {
            service.flag(attacker, CheckType.AUTOCLICKER, 1.2,
                    attacksPerSecond + " CPS");
        } else {
            // Bot-like click consistency at high speed.
            List<Long> intervals = trace.attackIntervals();
            if (intervals.size() >= 15) {
                double sum = 0.0;
                for (long interval : intervals) {
                    sum += interval;
                }
                double average = sum / intervals.size();
                double variance = 0.0;
                for (long interval : intervals) {
                    variance += (interval - average) * (interval - average);
                }
                double deviation = Math.sqrt(variance / intervals.size());
                if (average < 90.0 && deviation < 8.0) {
                    service.flag(attacker, CheckType.AUTOCLICKER, 0.8,
                            String.format(Locale.ROOT, "robot clicks (%.0fms ± %.1f)", average, deviation));
                }
            }
        }

        // Reach: hitting beyond vanilla's ~3 block reach.
        double reach = eyeToBoxDistance(attacker, victim);
        if (reach > config.maxReach() * factor) {
            service.flag(attacker, CheckType.REACH,
                    1.0 + Math.min(3.0, reach - config.maxReach()),
                    String.format(Locale.ROOT, "%.1f blocks", reach));
        }

        // Multi-aura: attacking several distinct entities in a burst.
        int victims = trace.pushVictim(victim.getUniqueId(), now);
        if (victims >= 3) {
            service.flag(attacker, CheckType.MULTIAURA, 1.5, victims + " targets in 1.5s");
        }

        // Aim: landing hits while looking far away from the target.
        Location eye = attacker.getEyeLocation();
        Vector look = eye.getDirection();
        BoundingBox box = victim.getBoundingBox();
        Vector toTarget = new Vector(
                (box.getMinX() + box.getMaxX()) / 2.0,
                (box.getMinY() + box.getMaxY()) / 2.0,
                (box.getMinZ() + box.getMaxZ()) / 2.0).subtract(eye.toVector());
        if (look.lengthSquared() > 0.001 && toTarget.lengthSquared() > 0.001) {
            double dot = look.normalize().dot(toTarget.normalize());
            double angleDegrees = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
            if (angleDegrees > config.minAimAngle()) {
                service.flag(attacker, CheckType.AIM, 1.0,
                        String.format(Locale.ROOT, "hit at %.0f°", angleDegrees));
            }
        }
    }

    // ---------------------------------------------------------- interaction

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!checked(player)) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        // Reveal hidden ores around anything the player touches (anti-xray).
        obfuscator.revealAround(player, block.getLocation(), 2);

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        boolean leftOrRight = event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK
                || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;
        if (!leftOrRight) {
            return;
        }
        PlayerTrace trace = service.trace(player);
        long now = System.currentTimeMillis();
        if (now - trace.joinedAtMillis() < config.joinGraceSeconds() * 1000L) {
            return;
        }
        int ping = player.getPing();
        double tps = AntiCheatService.currentTps();
        if (PingGuard.shouldSkip(ping, tps, config.maxPing(), config.minTps())) {
            return;
        }
        double factor = PingGuard.allowanceFactor(ping, tps);
        Location eye = player.getEyeLocation();
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        double distance = eye.distance(center);
        if (distance > config.maxInteractReach() * factor) {
            service.flag(player, CheckType.INTERACT_REACH, 1.2,
                    String.format(Locale.ROOT, "%.1f blocks", distance));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!checked(player)) {
            return;
        }
        PlayerTrace trace = service.trace(player);
        long now = System.currentTimeMillis();
        if (now - trace.joinedAtMillis() < config.joinGraceSeconds() * 1000L) {
            obfuscator.revealAround(player, event.getBlock().getLocation(), 2);
            return;
        }
        int ping = player.getPing();
        double tps = AntiCheatService.currentTps();
        double factor = PingGuard.allowanceFactor(ping, tps);

        int breaksPerSecond = trace.pushBreak(now);
        if (breaksPerSecond > (int) (config.maxBreaksPerSecond() * factor)) {
            service.flag(player, CheckType.FASTPLACE, 1.0, breaksPerSecond + " breaks/s");
        }

        // X-Ray mining statistics: score how hidden each broken ore was.
        Block block = event.getBlock();
        if (OreObfuscator.isOre(block.getType())) {
            int exposed = OreObfuscator.exposedFaces(block);
            trace.addOreSample(XrayStats.hiddenness(exposed));
            if (trace.oreCount() >= config.xrayWindow()) {
                double vl = XrayStats.violationLevel(trace.hiddenWeightSum(), trace.oreCount(),
                        config.xrayMinRatio(), config.xrayMinSamples());
                if (vl > 0) {
                    service.flag(player, CheckType.XRAY, vl,
                            String.format(Locale.ROOT, "%.2f hiddenness over %d ores",
                                    XrayStats.averageHiddenness(trace.hiddenWeightSum(), trace.oreCount()),
                                    trace.oreCount()));
                }
                trace.clearOreWindow();
            }
        }

        obfuscator.revealAround(player, block.getLocation(), 2);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!checked(player)) {
            return;
        }
        PlayerTrace trace = service.trace(player);
        long now = System.currentTimeMillis();
        if (now - trace.joinedAtMillis() < config.joinGraceSeconds() * 1000L) {
            return;
        }
        int ping = player.getPing();
        double tps = AntiCheatService.currentTps();
        double factor = PingGuard.allowanceFactor(ping, tps);
        int placesPerSecond = trace.pushPlace(now);
        if (placesPerSecond > (int) (config.maxPlacesPerSecond() * factor)) {
            service.flag(player, CheckType.FASTPLACE, 1.0, placesPerSecond + " places/s");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!checked(player)) {
            return;
        }
        PlayerTrace trace = service.trace(player);
        long gap = trace.pushConsume(System.currentTimeMillis());
        if (gap < 1200L) {
            service.flag(player, CheckType.FASTUSE, 0.8, gap + "ms between consumes");
        }
    }

    // -------------------------------------------------------------- helper

    private boolean checked(Player player) {
        return config.anticheatEnabled()
                && !player.hasPermission("veltryx.anticheat.bypass")
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR;
    }

    private static boolean isClimbing(Material material) {
        for (Material candidate : CLIMBING) {
            if (candidate == material) {
                return true;
            }
        }
        return false;
    }

    /** Speed multiplier from potion effects and terrain (never below 1.0). */
    private static double situationalSpeedMultiplier(Player player, Location to) {
        double multiplier = 1.0;
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.SPEED)) {
                multiplier *= 1.0 + 0.2 * (effect.getAmplifier() + 1);
            }
        }
        Material below = to.clone().subtract(0, 0.2, 0).getBlock().getType();
        if (below == Material.ICE || below == Material.PACKED_ICE
                || below == Material.BLUE_ICE || below == Material.FROSTED_ICE) {
            multiplier *= 1.6;
        }
        if (below == Material.SLIME_BLOCK || below == Material.HONEY_BLOCK) {
            multiplier *= 1.5;
        }
        return Math.min(3.0, multiplier);
    }

    /** Distance from the attacker's eyes to the closest point of the victim's hitbox. */
    private static double eyeToBoxDistance(Player attacker, Entity victim) {
        Location eye = attacker.getEyeLocation();
        BoundingBox box = victim.getBoundingBox();
        double x = clamp(eye.getX(), box.getMinX(), box.getMaxX());
        double y = clamp(eye.getY(), box.getMinY(), box.getMaxY());
        double z = clamp(eye.getZ(), box.getMinZ(), box.getMaxZ());
        return eye.distance(new Location(eye.getWorld(), x, y, z));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
