package dev.superseller.veltryx.anticheat.listener;

import dev.superseller.veltryx.anticheat.AntiCheatService;
import dev.superseller.veltryx.anticheat.FlyGuard;
import dev.superseller.veltryx.anticheat.JesusGuard;
import dev.superseller.veltryx.anticheat.MovementEnvironment;
import dev.superseller.veltryx.anticheat.SpeedGuard;
import dev.superseller.veltryx.anticheat.CheckType;
import dev.superseller.veltryx.anticheat.PingGuard;
import dev.superseller.veltryx.anticheat.PlayerTrace;
import dev.superseller.veltryx.anticheat.XrayStats;
import dev.superseller.veltryx.anticheat.xray.OreObfuscator;
import dev.superseller.veltryx.config.VeltryxConfig;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.LongSupplier;
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

    private final AntiCheatService service;
    private final VeltryxConfig config;
    private final OreObfuscator obfuscator;
    private final LongSupplier nanoClock;

    public AntiCheatListener(AntiCheatService service, VeltryxConfig config, OreObfuscator obfuscator) {
        this(service, config, obfuscator, System::nanoTime);
    }

    /** Monotonic movement clock can be supplied for deterministic regression tests. */
    public AntiCheatListener(AntiCheatService service, VeltryxConfig config, OreObfuscator obfuscator,
                             LongSupplier nanoClock) {
        this.service = service;
        this.config = config;
        this.obfuscator = obfuscator;
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    // ------------------------------------------------------------ lifecycle

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        service.startTracking(event.getPlayer());
        obfuscator.scanNow(event.getPlayer());
    }

    /** The client just received a chunk — fake its hidden ore immediately. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChunkLoad(io.papermc.paper.event.packet.PlayerChunkLoadEvent event) {
        org.bukkit.Chunk chunk = event.getChunk();
        obfuscator.chunkSent(event.getPlayer(), chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.stopTracking(event.getPlayer().getUniqueId());
        obfuscator.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        obfuscator.scanNow(player);
        if (checked(player)) {
            service.trace(player).skipNextMove();
            service.trace(player).setSpeedGraceUntilMillis(System.currentTimeMillis() + 450L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        obfuscator.scanNow(player);
        if (checked(player)) {
            service.trace(player).skipNextMove();
            service.trace(player).setSpeedGraceUntilMillis(System.currentTimeMillis() + 450L);
        }
    }

    // ----------------------------------------------------------- movement

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!checked(player)) {
            PlayerTrace existing = service.traceIfPresent(player.getUniqueId());
            if (existing != null) existing.resetMovementChecks();
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from == null || to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            PlayerTrace existing = service.traceIfPresent(player.getUniqueId());
            if (existing != null) existing.resetMovementChecks();
            return;
        }

        PlayerTrace trace = service.trace(player);
        long nowNanos = nanoClock.getAsLong();
        long nowMillis = System.currentTimeMillis();
        boolean skipNext = trace.consumeSkipNextMove();
        if (!trace.positionInitialized() || skipNext) {
            trace.resetMovementChecks();
            trace.recordPosition(to.getX(), to.getY(), to.getZ(), nowNanos);
            trace.touchFlySample(nowNanos);
            obfuscator.scanAround(player);
            return;
        }

        int ping = player.getPing();
        double tps = AntiCheatService.currentTps();
        if (PingGuard.shouldSkip(ping, tps, config.maxPing(), config.minTps())
                || nowMillis - trace.joinedAtMillis() < config.joinGraceSeconds() * 1000L
                || trace.hasSpeedGrace(nowMillis)) {
            trace.resetMovementChecks();
            trace.recordPosition(to.getX(), to.getY(), to.getZ(), nowNanos);
            trace.touchFlySample(nowNanos);
            return;
        }

        // Use the event's endpoints, not a previously proposed destination:
        // another plugin may have changed or cancelled that earlier move.
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        boolean positionChanged = dx != 0.0 || dy != 0.0 || dz != 0.0;
        double positionSeconds = trace.secondsSinceLastMove(nowNanos);
        boolean skipPhysics = player.isInsideVehicle() || player.isGliding() || player.isFlying()
                || player.isDead() || player.isRiptiding();
        if (skipPhysics) {
            trace.resetMovementChecks();
            trace.recordPosition(to.getX(), to.getY(), to.getZ(), nowNanos);
            trace.touchFlySample(nowNanos);
            return;
        }

        // Looking around can emit extra move events. Do not count them toward
        // timer/speed, or split a real falling sample into artificial zero-dy
        // samples. After a sustained positionless interval they may still
        // provide time-based evidence of an unsupported stationary hover.
        if (!positionChanged && positionSeconds < 0.20) {
            return;
        }
        double flySeconds = trace.secondsSinceLastFlySample(nowNanos);
        trace.touchFlySample(nowNanos);
        if (positionChanged) {
            trace.recordPosition(to.getX(), to.getY(), to.getZ(), nowNanos);
        }

        MovementEnvironment.Sample start = MovementEnvironment.sample(player, from);
        if (!start.known()) {
            trace.resetMovementChecks();
            return;
        }
        MovementEnvironment.Sample end = positionChanged ? MovementEnvironment.sample(player, to) : start;
        if (!end.known()) {
            // A non-owned/unloaded chunk or an impractically large hitbox is
            // not evidence. Never force-load a chunk for anti-cheat sampling.
            trace.resetMovementChecks();
            return;
        }

        double factor = PingGuard.allowanceFactor(ping, tps);
        if (positionChanged) {
            // Consecutive windows, rather than each event after one burst;
            // rotation-only events do not count as extra positional packets.
            int allowance = (int) (28.0 * factor) + 4;
            if (dy < -0.08) allowance *= 2;
            int excessiveRate = trace.recordTimerMove(nowNanos, allowance);
            if (excessiveRate > 0) {
                service.flag(player, CheckType.TIMER, 1.0, excessiveRate + " position moves/s (sustained)");
            }
            if (dy > 0.12) trace.markJump(nowNanos);
        }

        boolean climbing = start.climbing() || end.climbing();
        // The server's fluid state also covers waterlogged blocks, whose
        // material is not WATER even though they slow the player.
        boolean inLiquid = start.liquid() || end.liquid() || player.isInWater() || player.isSwimming();
        boolean constrained = start.powderSnow() || end.powderSnow() || start.slowingBlock() || end.slowingBlock();
        boolean feetInLiquid = end.feet() == Material.WATER || end.feet() == Material.LAVA;

        // Retain the water-surface check, but don't carry a skimming streak
        // into another medium or through a period without reliable samples.
        if (feetInLiquid && !player.isSwimming() && !climbing && !constrained) {
            if (positionChanged && positionSeconds > 0.02 && positionSeconds <= FlyGuard.MAX_SAMPLE_GAP_SECONDS) {
                if (JesusGuard.skimming(end.headInWater(), end.supported(), dy, player.getFallDistance())) {
                    // A burst can deliver a normal tick's displacement in
                    // much less than a tick of wall-clock time.
                    double horizontal = Math.sqrt(dx * dx + dz * dz) / Math.max(0.05, positionSeconds);
                    trace.setJesusStreak(horizontal > 2.8 * factor ? trace.jesusStreak() + 1 : 0);
                    if (JesusGuard.shouldFlag(trace.jesusStreak()) && service.plan(CheckType.JESUS).enabled()
                            && trace.shouldFlagMovement(CheckType.JESUS, nowNanos)) {
                        service.flag(player, CheckType.JESUS, 1.2,
                                String.format(Locale.ROOT, "%.1f b/s on water", horizontal));
                    }
                } else {
                    trace.setJesusStreak(0);
                }
            } else if (positionChanged && positionSeconds > FlyGuard.MAX_SAMPLE_GAP_SECONDS) {
                trace.setJesusStreak(0);
            }
        } else {
            trace.setJesusStreak(0);
        }

        // Both endpoints must be deeply inside actual collision boxes. A solid
        // material alone does not mean the occupied part of a slab/stair is
        // solid, and orientation-only events are not phasing.
        if (positionChanged && positionSeconds <= FlyGuard.MAX_SAMPLE_GAP_SECONDS
                && !inLiquid && !climbing && !constrained && dy >= -0.08
                && start.embedded() && end.embedded()) {
            trace.setPhaseStreak(trace.phaseStreak() + 1);
            trace.resetPhysicsCounters();
            // A transient deep overlap can be caused by a piston or another
            // plugin. Require repeated evidence and rate-limit escalation.
            if (trace.phaseStreak() >= 3 && service.plan(CheckType.PHASE).enabled()
                    && trace.shouldFlagMovement(CheckType.PHASE, nowNanos)) {
                service.flag(player, CheckType.PHASE, 3.0, start.feet() + " -> " + end.feet());
            }
            return;
        }
        trace.setPhaseStreak(0);

        if (climbing || inLiquid || constrained) {
            // Snow, cobwebs and berry bushes alter vertical physics even
            // when the player's centre is outside the touched block.
            trace.resetPhysicsCounters();
            return;
        }

        boolean onGround = start.supported() || end.supported();
        boolean falling = dy < FlyGuard.REAL_FALL_DY || player.getFallDistance() > 0.4F;
        boolean elytraAir = !onGround && hasElytra(player);
        if (positionChanged && !elytraAir && positionSeconds > 0.02
                && positionSeconds <= FlyGuard.MAX_SAMPLE_GAP_SECONDS) {
            // Position packets can arrive close together after jitter even
            // though each represents a full game tick of movement. Never
            // infer speed from an interval shorter than one nominal tick.
            double speedSeconds = Math.max(0.05, positionSeconds);
            double horizontal = Math.sqrt(dx * dx + dz * dz) / speedSeconds;
            double multiplier = SpeedGuard.situationalMultiplier(
                    potionAmplifier(player, PotionEffectType.SPEED),
                    start.ice() || end.ice(), start.slimeOrHoney() || end.slimeOrHoney(),
                    start.soulTerrain() || end.soulTerrain(), trace.inJumpChain(nowNanos));
            double limit = SpeedGuard.limit(config.maxMoveSpeed(), factor, multiplier);
            int streak = trace.speedExcessStreak();
            int verdict = SpeedGuard.verdict(horizontal, limit, config.speedHardCap(), streak);
            boolean grounded = (onGround || trace.inJumpChain(nowNanos)) && !falling;
            CheckType correctionCheck = grounded ? CheckType.SPEED : CheckType.FLY;
            if (verdict == SpeedGuard.OK || !service.plan(correctionCheck).enabled()) {
                trace.setSpeedExcessStreak(0);
            } else {
                trace.setSpeedExcessStreak(streak + 1);
                String detail = String.format(Locale.ROOT, "%.1f b/s (limit %.1f)", horizontal, limit);
                if (verdict == SpeedGuard.ROLLBACK && streak >= 1) {
                    if (config.serverAuthority()) {
                        double maxDist = limit * config.speedHardCap() * speedSeconds;
                        double[] clamped = SpeedGuard.clampDelta(dx, dz, maxDist);
                        double nx = from.getX() + clamped[0];
                        double nz = from.getZ() + clamped[1];
                        event.setTo(new Location(from.getWorld(), nx, to.getY(), nz,
                                to.getYaw(), to.getPitch()));
                        trace.resetMovementChecks();
                        trace.recordPosition(nx, to.getY(), nz, nowNanos);
                        trace.touchFlySample(nowNanos);
                        trace.setSpeedGraceUntilMillis(nowMillis + 250L);
                        if (trace.shouldFlagMovement(correctionCheck, nowNanos)) {
                            service.flag(player, correctionCheck, 2.5, detail + " — clamped");
                        }
                        obfuscator.scanAround(player);
                        return;
                    }
                    if (trace.shouldFlagMovement(correctionCheck, nowNanos)) {
                        service.flag(player, correctionCheck, 2.5, detail + " — hard cap");
                    }
                } else if (grounded && verdict == SpeedGuard.FLAG
                        && trace.shouldFlagMovement(CheckType.SPEED, nowNanos)) {
                    service.flag(player, CheckType.SPEED,
                            1.0 + Math.min(3.0, (horizontal - limit) / 2.0), detail);
                }
            }
        } else if (positionChanged) {
            // Do not join excess samples across a gap, a packet burst, or an
            // elytra transition for which speed could not be evaluated.
            trace.setSpeedExcessStreak(0);
        }

        // A landing or takeoff at either endpoint resets accumulated airtime,
        // including when only the player's edge overlaps a support shape.
        boolean honeySlide = dy < 0.0 && (start.honeyContact() || end.honeyContact());
        // REAL_FALL_DY is a per-tick threshold. Scale the positional delta
        // to 50 ms so multiple sub-tick updates do not make a normal fast
        // fall look like a long series of slow-fall samples.
        double tickEquivalentDy = positionChanged && positionSeconds > 0.0
                && positionSeconds <= FlyGuard.MAX_SAMPLE_GAP_SECONDS
                ? dy * 0.05 / Math.max(0.005, positionSeconds) : dy;
        if (!service.plan(CheckType.FLY).enabled()) {
            // Re-enabling a check must not punish evidence collected while it
            // was disabled.
            trace.setAirSeconds(0.0);
        } else {
            trace.setAirSeconds(FlyGuard.step(trace.airSeconds(), tickEquivalentDy, flySeconds, onGround || honeySlide,
                    potionAmplifier(player, PotionEffectType.LEVITATION) >= 0,
                    potionAmplifier(player, PotionEffectType.SLOW_FALLING) >= 0));
            double airborneSeconds = trace.airSeconds();
            double maxAirSeconds = FlyGuard.maxAirSeconds(potionAmplifier(player, PotionEffectType.JUMP_BOOST), factor);
            if (airborneSeconds > maxAirSeconds && trace.shouldFlagMovement(CheckType.FLY, nowNanos)) {
                service.flag(player, CheckType.FLY,
                        1.2 + Math.min(2.0, airborneSeconds - maxAirSeconds),
                        String.format(Locale.ROOT, "airborne %.1fs without falling (dy=%.2f)", airborneSeconds, dy));
                if (config.serverAuthority() && nowMillis - trace.flyPullAtMillis() >= 500L) {
                    trace.setFlyPullAtMillis(nowMillis);
                    Vector velocity = player.getVelocity();
                    player.setVelocity(new Vector(velocity.getX() * 0.92, -0.32, velocity.getZ() * 0.92));
                }
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

    /** Amplifier of an active potion effect, or -1 when the effect is absent. */
    private static int potionAmplifier(Player player, PotionEffectType type) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(type)) {
                return effect.getAmplifier();
            }
        }
        return -1;
    }

    /** Whether the player is wearing an elytra (pre/post glide momentum). */
    private static boolean hasElytra(Player player) {
        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
        return inventory != null && inventory.getChestplate() != null
                && inventory.getChestplate().getType() == Material.ELYTRA;
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
