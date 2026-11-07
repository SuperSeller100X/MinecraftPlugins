package dev.superseller.veltryx.anticheat.listener;

import dev.superseller.veltryx.anticheat.AntiCheatService;
import dev.superseller.veltryx.anticheat.SpeedGuard;
import org.bukkit.util.Vector;
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
        // After teleports and server-authority corrections the next movement
        // samples are artifacts — they must never count toward any check.
        if (trace.hasSpeedGrace(nowMillis)) {
            trace.recordPosition(to.getX(), to.getY(), to.getZ(), nowNanos);
            trace.pushMoveTime(nowMillis);
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

        // Sprint-jump chains are the fastest legitimate vanilla movement —
        // remember them so the speed limit covers the whole hop chain.
        if (dy > 0.12) {
            trace.markJump(nowNanos);
        }

        boolean skipPhysics = player.isInsideVehicle() || player.isGliding() || player.isFlying()
                || player.isDead() || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR || player.isRiptiding();

        // Timer: far more position updates than the 20 TPS game loop produces.
        // Fast movement (elytra, vehicles, falling) legitimately produces extra
        // position packets, so the allowance doubles there — no false flags.
        int timerAllowance = (int) (22.0 * factor) + 4;
        if (skipPhysics || dy < -0.08) {
            timerAllowance *= 2;
        }
        if (trace.moveEventsLastSecond() > timerAllowance) {
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

        // Phase: moving while fully inside solid blocks. Fast descents are
        // exempt — client prediction legitimately clips into blocks while
        // falling and must never false-flag.
        if (!inLiquid && !climbing && dy >= -0.08
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
        // The limit covers the fastest legitimate vanilla movement (sprint-jump
        // chains, speed potions, ice, soul speed, slime launches) and grows with
        // ping/lag — no false flags. Falling and elytra carry unbounded momentum
        // and never flag. Mild excess is only counted after THREE consecutive
        // samples; movement above the hard cap is impossible for vanilla clients
        // and gets rolled back by server authority.
        boolean falling = dy < -0.08 || player.getFallDistance() > 0.4F;
        boolean elytraAir = !onGround && hasElytra(player);
        if (!falling && !elytraAir && seconds > 0.02 && seconds < 2.0) {
            double horizontal = Math.sqrt(dx * dx + dz * dz) / seconds;
            Material below = to.clone().subtract(0, 0.2, 0).getBlock().getType();
            double multiplier = SpeedGuard.situationalMultiplier(
                    potionAmplifier(player, PotionEffectType.SPEED),
                    below == Material.ICE || below == Material.PACKED_ICE
                            || below == Material.BLUE_ICE || below == Material.FROSTED_ICE,
                    below == Material.SLIME_BLOCK || below == Material.HONEY_BLOCK,
                    below == Material.SOUL_SAND || below == Material.SOUL_SOIL,
                    trace.inJumpChain(nowNanos));
            double limit = SpeedGuard.limit(config.maxMoveSpeed(), factor, multiplier);
            int streak = trace.speedExcessStreak();
            int verdict = SpeedGuard.verdict(horizontal, limit, config.speedHardCap(), streak);
            if (verdict == SpeedGuard.OK) {
                trace.setSpeedExcessStreak(0);
            } else {
                trace.setSpeedExcessStreak(streak + 1);
                String detail = String.format(Locale.ROOT, "%.1f b/s (limit %.1f)", horizontal, limit);
                if (verdict == SpeedGuard.ROLLBACK) {
                    // Server authority, smoothly: instead of snapping the player
                    // back (rubber-band = lag feel), the movement is clamped
                    // along its own direction to the hard cap. The player glides
                    // at the limit — speed hacks cannot outrun it and nothing
                    // ever looks like lag or bugs the player into geometry.
                    if (config.serverAuthority()) {
                        double maxDist = limit * config.speedHardCap() * seconds;
                        double[] clamped = SpeedGuard.clampDelta(dx, dz, maxDist);
                        double nx = from.getX() + clamped[0];
                        double nz = from.getZ() + clamped[1];
                        event.setTo(new Location(from.getWorld(), nx, to.getY(), nz,
                                to.getYaw(), to.getPitch()));
                        trace.recordPosition(nx, to.getY(), nz, nowNanos);
                        trace.setSpeedGraceUntilMillis(nowMillis + 250L);
                    }
                    service.flag(player, CheckType.SPEED, streak >= 1 ? 2.5 : 0.8, detail + " — clamped");
                } else if (verdict == SpeedGuard.FLAG) {
                    service.flag(player, CheckType.SPEED, 1.0 + Math.min(3.0, (horizontal - limit) / 2.0), detail);
                }
            }
        }

        // Fly: staying airborne far longer than any jump without really falling.
        // Slow descents (dy >= -0.08) count as flight too — hovering and
        // drifting must be stopped even without sprinting. Fast descents decay
        // the counter instead of resetting it, so bobbing cannot evade
        // detection while real falls never accumulate. Levitation and slow
        // falling are legitimate air states and high jump-boost levels
        // legitimately extend air time; creative, spectator, elytra, vehicles
        // and riptide never reach this check at all.
        if (onGround || potionAmplifier(player, PotionEffectType.LEVITATION) >= 0
                || potionAmplifier(player, PotionEffectType.SLOW_FALLING) >= 0) {
            trace.setAirTicks(0);
        } else if (dy < -0.08) {
            trace.setAirTicks(Math.max(0, trace.airTicks() - 4));
        } else {
            trace.setAirTicks(trace.airTicks() + 1);
            int jumpBoost = potionAmplifier(player, PotionEffectType.JUMP_BOOST);
            double maxAirSeconds = (1.3 + 0.12 * Math.max(0, jumpBoost)) * factor;
            double airborneSeconds = trace.airTicks() * 0.05;
            if (airborneSeconds > maxAirSeconds) {
                service.flag(player, CheckType.FLY,
                        1.2 + Math.min(2.0, (airborneSeconds - maxAirSeconds)),
                        String.format(Locale.ROOT, "airborne %.1fs without falling (dy=%.2f)",
                                airborneSeconds, dy));
                // Server authority, smoothly: once flight is confirmed (never
                // fires for legitimate air time), a gentle gravity-like pull
                // drains the hacked elevation. Velocity is client-predicted, so
                // this looks and feels like normal falling — no rubber-band
                // stutter, no "lag in the air", no getting stuck — while the
                // kick/ban escalation handles anything that keeps defying it.
                if (config.serverAuthority()) {
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

    private static boolean isClimbing(Material material) {
        for (Material candidate : CLIMBING) {
            if (candidate == material) {
                return true;
            }
        }
        return false;
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
