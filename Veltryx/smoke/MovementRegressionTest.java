package dev.superseller.veltryx.smoke;

import dev.superseller.veltryx.anticheat.AntiCheatService;
import dev.superseller.veltryx.anticheat.CheckType;
import dev.superseller.veltryx.anticheat.MovementEnvironment;
import dev.superseller.veltryx.anticheat.PlayerTrace;
import dev.superseller.veltryx.anticheat.listener.AntiCheatListener;
import dev.superseller.veltryx.anticheat.xray.OreObfuscator;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.model.FlagEntry;
import dev.superseller.veltryx.storage.DataStore;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bukkit.util.VoxelShape;

/**
 * Deterministic listener-level tests against local Bukkit doubles, not a real
 * Paper/Folia server or Minecraft client. Times are monotonic simulated time.
 */
public final class MovementRegressionTest {
    private static int passed, failed;
    private static final BoundingBox CUBE = box(0, 0, 0, 1, 1, 1);
    private static final BoundingBox SLAB = box(0, 0, 0, 1, 0.5, 1);
    private static final BoundingBox FENCE = box(0.375, 0, 0.375, 0.625, 1.5, 0.625);
    private static final BoundingBox STAIR_TOP = box(0, 0.5, 0.5, 1, 1, 1);

    public static void main(String[] args) {
        try {
            geometry();
            jumpOntoAndOffEdge();
            powderSnow();
            mediumTransitions();
            hoverAndFalling();
            phaseAndSpeed();
            timerAndRotation();
            unavailableChunks();
        } finally {
            Bukkit.testRegionOwnership = null;
        }
        System.out.println("Movement regression: " + passed + " passed, " + failed + " failed.");
        if (failed > 0) System.exit(1);
    }

    private static void geometry() {
        Fixture f = new Fixture();
        f.world.put(0, 64, 0, Material.STONE, CUBE);
        check("edge footprint gets support even when center is outside", f.sample(1.29, 65, 0.5).supported());
        check("no support when footprint misses the block", !f.sample(1.31, 65, 0.5).supported());
        check("no floating 0.13 above support", !f.sample(0.5, 65.13, 0.5).supported());
        check("deep penetration is not ground support", !f.sample(0.5, 64.9, 0.5).supported());
        f.world.put(-2, 64, -2, Material.STONE, CUBE);
        check("negative-coordinate edge uses floor", f.sample(-0.71, 65, -1.5).supported());
        check("negative-coordinate near miss stays airborne", !f.sample(-0.69, 65, -1.5).supported());
        f.world.put(3, 64, 0, Material.STONE_SLAB, SLAB);
        check("lower slab surface supports", f.sample(3.5, 64.5, 0.5).supported());
        check("air above a slab is not a full cube", !f.sample(3.5, 65, 0.5).supported());
        check("standing on slab is not embedded", !f.sample(3.5, 64.5, 0.5).embedded());
        f.world.put(5, 64, 0, Material.OAK_FENCE, FENCE);
        check("1.5-high fence support is found", f.sample(5.5, 65.5, 0.5).supported());
        check("fence empty side is not support", !f.sample(5.95, 65.5, 0.5).supported());
        f.world.put(6, 64, 0, Material.SCAFFOLDING, box(0, 0.875, 0, 1, 1, 1));
        check("scaffolding's actual top surface supports", f.sample(6.5, 65, 0.5).supported());
        f.world.put(8, 64, 0, Material.STONE_STAIRS, SLAB, STAIR_TOP);
        var stair = f.sample(8.5, 64.5, 0.20);
        check("lower stair tread supports", stair.supported());
        check("stair tread does not imply phase", !stair.embedded());
        f.world.put(10, 64, 0, Material.SHULKER_BOX, box(0, 0, 0, 1.5, 1, 1));
        check("extended collision shape supports beyond its cell", f.sample(11.5, 65, 0.5).supported());
        check("extended collision near miss is not support", !f.sample(11.81, 65, 0.5).supported());
        f.world.put(12, 64, 0, Material.POWDER_SNOW);
        check("powder snow overlaps player edge", f.sample(13.2, 64.4, 0.5).powderSnow());
        check("nearby but untouched powder snow is not exempt", !f.sample(13.31, 64.4, 0.5).powderSnow());
        check("powder snow at boot contact is covered", f.sample(12.5, 65.125, 0.5).powderSnow());
        check("hover above powder is not exempt", !f.sample(12.5, 65.13, 0.5).powderSnow());
        check("powder can overlap the upper body", f.sample(12.5, 63.5, 0.5).powderSnow());
        f.world.put(14, 64, 0, Material.HONEY_BLOCK, box(0, 0, 0, 1, 0.9375, 1));
        check("honey wall contact is detected", f.sample(15.32, 64.2, 0.5).honeyContact());
        check("distant honey is not a broad exemption", !f.sample(15.38, 64.2, 0.5).honeyContact());
        f.player.width = 16; f.player.height = 32;
        check("huge hitbox is safely marked unknown", !f.sample(0.5, 64, 0.5).known());
    }

    private static void jumpOntoAndOffEdge() {
        Fixture f = new Fixture();
        f.world.put(1, 63, 0, Material.STONE, CUBE);
        f.world.put(2, 63, 0, Material.STONE, CUBE);
        f.world.put(0, 64, 0, Material.STONE, CUBE);
        f.init(1.45, 64, 0.5);
        double[][] path = {
                {1.45,64.42},{1.42,64.75},{1.35,65.00},{1.25,65.16},
                {1.20,65.24},{1.16,65.25},{1.15,65.18},{1.15,65.02},{1.15,65.00},
                {1.18,65.42},{1.25,65.75},{1.32,66.00},{1.40,66.16},
                {1.48,66.24},{1.55,66.25},{1.62,66.18},{1.69,66.03},
                {1.76,65.80},{1.83,65.50},{1.90,65.12},{1.97,64.68},
                {2.04,64.16},{2.10,64.00}
        };
        for (int i = 0; i < path.length; i++) {
            Move move = f.go(path[i][0], path[i][1], 0.5, 50);
            check("edge jump move not cancelled " + i, !move.cancelled);
            check("edge jump move not clamped " + i, !move.changedTo);
            if (i == 8 || i == 9) check("edge landing/takeoff resets airtime " + i, f.trace().airSeconds() == 0.0);
        }
        noConsequences("immediate jump on/off block edge", f);
    }

    private static void powderSnow() {
        Fixture f = new Fixture();
        f.world.put(0, 64, 0, Material.POWDER_SNOW);
        // The player centre is in the adjacent air block; the hitbox overlaps snow.
        f.init(1.20, 64.40, 0.5);
        f.trace().setAirSeconds(0.79);
        f.trace().setSpeedExcessStreak(2);
        f.trace().setJesusStreak(2);
        for (int i = 0; i < 80; i++) {
            double x = i % 2 == 0 ? 1.19 : 1.20;
            double y = 64.35 + (i % 8) * 0.025;
            Move move = f.go(x, y, 0.5, 50);
            check("powder snow move not clamped " + i, !move.changedTo && !move.cancelled);
        }
        check("powder clears accumulated flight", f.trace().airSeconds() == 0.0);
        check("powder clears speed streak", f.trace().speedExcessStreak() == 0);
        check("powder clears stale jesus streak", f.trace().jesusStreak() == 0);
        // One endpoint still overlaps snow during the jump out; subsequent
        // normal airborne samples are checked again.
        f.go(1.20, 64.95, 0.5, 50);
        f.go(1.20, 65.37, 0.5, 50);
        check("leaving snow clears old airtime", f.trace().airSeconds() == 0.0);
        f.go(1.30, 65.70, 0.5, 50);
        f.go(1.40, 65.95, 0.5, 50);
        f.go(1.45, 66.10, 0.5, 50);
        f.go(1.45, 66.15, 0.5, 50);
        f.go(1.45, 66.10, 0.5, 50);
        f.go(1.40, 65.95, 0.5, 50);
        f.go(1.30, 65.70, 0.5, 50);
        f.go(1.20, 65.35, 0.5, 50);
        f.go(1.20, 64.95, 0.5, 50);
        noConsequences("jumping in/out of powder snow", f);

        Fixture nearby = new Fixture();
        nearby.world.put(0, 64, 0, Material.POWDER_SNOW);
        nearby.init(1.5, 65.5, 0.5);
        for (int i = 0; i < 25; i++) nearby.go(1.5 + (i + 1) * 0.002, 65.5, 0.5, 50);
        check("hover beside but not touching powder is detected", nearby.hasFlag(CheckType.FLY));
        check("hover beside powder can still be corrected", nearby.player.velocityWrites > 0);
        Fixture exited = new Fixture();
        exited.world.put(0, 64, 0, Material.POWDER_SNOW);
        exited.init(0.5, 64.5, 0.5);
        exited.go(0.5, 65.5, 0.5, 50); // from inside powder
        for (int i = 0; i < 30; i++) exited.go(0.5 + (i + 1) * 0.002, 65.5, 0.5, 50);
        check("hover detection resumes after leaving powder", exited.hasFlag(CheckType.FLY));
    }

    private static void mediumTransitions() {
        for (Material medium : List.of(Material.WATER, Material.BUBBLE_COLUMN, Material.LADDER,
                Material.TWISTING_VINES_PLANT, Material.WEEPING_VINES_PLANT, Material.CAVE_VINES_PLANT,
                Material.COBWEB, Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW)) {
            Fixture f = new Fixture();
            f.world.put(0, 64, 0, medium);
            f.init(0.5, 64.3, 0.5);
            f.trace().setAirSeconds(0.79); f.trace().setSpeedExcessStreak(2); f.trace().setJesusStreak(2);
            f.go(0.51, 64.31, 0.5, 50);
            check(medium + " clears fly counter", f.trace().airSeconds() == 0.0);
            check(medium + " clears speed streak", f.trace().speedExcessStreak() == 0);
            check(medium + " clears jesus streak", f.trace().jesusStreak() == 0);
            noConsequences("transition through " + medium, f);
        }
        Fixture waterWalk = new Fixture();
        waterWalk.world.put(0, 64, 0, Material.WATER);
        waterWalk.init(0.10, 64.9, 0.5);
        for (int i = 0; i < 5; i++) waterWalk.go(0.10 + (i + 1) * 0.15, 64.9, 0.5, 50);
        check("sustained water-surface skating still flags Jesus", waterWalk.hasFlag(CheckType.JESUS));
        for (int i = 0; i < 25; i++) waterWalk.go(i % 2 == 0 ? 0.7 : 0.85, 64.9, 0.5, 50);
        check("water skating flags are rate-limited", waterWalk.flagCount(CheckType.JESUS) <= 3);

        Fixture waterlogged = new Fixture();
        waterlogged.player.inWater = true;
        waterlogged.init(0.5, 64.5, 0.5);
        waterlogged.trace().setAirSeconds(0.79);
        waterlogged.go(0.51, 64.51, 0.5, 50);
        check("server water state covers waterlogged blocks", waterlogged.trace().airSeconds() == 0);
        noConsequences("waterlogged block fluid state", waterlogged);

        Fixture f = new Fixture();
        f.init(0.5, 100, 0.5);
        f.trace().setAirSeconds(0.79); f.trace().setSpeedExcessStreak(2); f.trace().setJesusStreak(2);
        f.player.vehicle = true;
        f.go(0.51, 100, 0.5, 50);
        check("vehicle clears movement counters", f.trace().airSeconds() == 0 && f.trace().speedExcessStreak() == 0
                && f.trace().jesusStreak() == 0);
        f.player.vehicle = false;
        f.trace().setAirSeconds(0.79); f.trace().setSpeedExcessStreak(2); f.trace().setJesusStreak(2);
        f.player.ping = 500;
        f.go(0.52, 100, 0.5, 50);
        check("high ping clears movement counters", f.trace().airSeconds() == 0 && f.trace().speedExcessStreak() == 0
                && f.trace().jesusStreak() == 0);
        f.player.ping = 0;
        f.trace().setAirSeconds(0.79); f.trace().setSpeedExcessStreak(2); f.trace().setJesusStreak(2);
        f.trace().skipNextMove();
        f.go(0.53, 100, 0.5, 50);
        check("teleport grace clears stale counters", f.trace().airSeconds() == 0 && f.trace().speedExcessStreak() == 0
                && f.trace().jesusStreak() == 0);
        f.trace().setAirSeconds(0.79); f.trace().setSpeedExcessStreak(2);
        f.go(0.54, 100, 0.5, 600);
        check("long event gap is not flight evidence", f.trace().airSeconds() == 0 && f.trace().speedExcessStreak() == 0);
        noConsequences("skipped physics and long gap", f);
    }

    private static void hoverAndFalling() {
        Fixture airBurst = new Fixture();
        airBurst.init(0.5, 100, 0.5);
        for (int i = 0; i < 100; i++) airBurst.go(0.5 + (i + 1) * 0.0001, 100, 0.5, 1);
        check("100 airborne updates represent 0.1s, not 5s", airBurst.trace().airSeconds() < 0.11);
        noConsequences("short airborne packet burst", airBurst);

        Fixture hover = new Fixture();
        hover.player.claimedOnGround = true; // untrusted client bit must not exempt hovering
        hover.init(0.5, 100, 0.5);
        for (int i = 0; i < 30; i++) hover.go(0.5 + (i + 1) * 0.003, 100, 0.5, 50);
        check("unsupported moving hover flags despite spoofed on-ground", hover.hasFlag(CheckType.FLY));
        check("confirmed hover gets the enabled authority pull", hover.player.velocityWrites > 0 && hover.player.velocity.getY() == -0.32);
        check("hover flags are rate-limited", hover.flagCount(CheckType.FLY) <= 2);

        Fixture stationary = new Fixture();
        stationary.init(0.5, 100, 0.5);
        for (int i = 0; i < 30; i++) stationary.rotate(50);
        check("sustained unsupported positionless hover still flags", stationary.hasFlag(CheckType.FLY));
        check("looking alone does not trigger timer", !stationary.hasFlag(CheckType.TIMER));

        Fixture slow = new Fixture();
        slow.init(0.5, 150, 0.5);
        for (int i = 0; i < 30; i++) slow.go(0.5 + (i + 1) * 0.002, 150 - (i + 1) * 0.02, 0.5, 50);
        check("unsupported slow-descent fly still flags", slow.hasFlag(CheckType.FLY));

        Fixture fall = new Fixture();
        fall.init(0.5, 200, 0.5);
        for (int i = 0; i < 55; i++) {
            fall.go(0.5, 200 - (i + 1) * 0.6, 0.5, 40);
            for (int r = 0; r < 10; r++) fall.rotate(1);
        }
        noConsequences("real fall with frequent rotation events", fall);
        Fixture splitFall = new Fixture();
        splitFall.init(0.5, 200, 0.5);
        for (int i = 0; i < 100; i++) splitFall.go(0.5, 200 - (i + 1) * 0.10, 0.5, 10);
        noConsequences("one second of a real fall split into sub-tick moves", splitFall);

        Fixture honey = new Fixture();
        honey.world.put(0, 64, 0, Material.HONEY_BLOCK, box(0, 0, 0, 1, 0.9375, 1));
        honey.init(1.32, 64.9, 0.5);
        for (int i = 0; i < 60; i++) honey.go(1.32, 64.9 - (i + 1) * 0.01, 0.5, 50);
        noConsequences("honey wall slide", honey);

        Fixture effect = new Fixture();
        effect.player.effects.add(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0));
        effect.init(0.5, 150, 0.5);
        for (int i = 0; i < 30; i++) effect.go(0.5 + (i + 1) * 0.002, 150 - (i + 1) * 0.02, 0.5, 50);
        noConsequences("slow falling potion", effect);

        Fixture noAuthority = new Fixture(true, false);
        noAuthority.init(0.5, 100, 0.5);
        for (int i = 0; i < 30; i++) noAuthority.go(0.5 + (i + 1) * 0.003, 100, 0.5, 50);
        check("flight still flags with authority off", noAuthority.hasFlag(CheckType.FLY));
        check("authority off never applies velocity", noAuthority.player.velocityWrites == 0);
        Fixture flyDisabled = new Fixture(false, true);
        flyDisabled.init(0.5, 100, 0.5);
        for (int i = 0; i < 30; i++) flyDisabled.go(0.5 + (i + 1) * 0.003, 100, 0.5, 50);
        check("disabled fly does not flag", !flyDisabled.hasFlag(CheckType.FLY));
        check("disabled fly does not pull player", flyDisabled.player.velocityWrites == 0);
        check("disabled fly retains no stale airtime", flyDisabled.trace().airSeconds() == 0.0);
        flyDisabled.yaml.set("anticheat.checks.movement.fly.enabled", true);
        flyDisabled.service.reload();
        flyDisabled.go(0.593, 100, 0.5, 50);
        check("re-enabled fly does not use disabled-period evidence", !flyDisabled.hasFlag(CheckType.FLY)
                && flyDisabled.player.velocityWrites == 0);
        for (int i = 0; i < 22; i++) flyDisabled.go(0.596 + i * 0.003, 100, 0.5, 50);
        check("re-enabled fly detects fresh hover", flyDisabled.hasFlag(CheckType.FLY));
    }

    private static void phaseAndSpeed() {
        Fixture slabs = new Fixture();
        slabs.world.put(0, 64, 0, Material.STONE_SLAB, SLAB);
        slabs.init(0.30, 64.5, 0.5);
        for (int i = 0; i < 40; i++) slabs.go(0.30 + (i % 20) * 0.01, 64.5, 0.5, 50);
        noConsequences("walking inside the material cell on a lower slab", slabs);
        Fixture stairs = new Fixture();
        stairs.world.put(0, 64, 0, Material.STONE_STAIRS, SLAB, STAIR_TOP);
        stairs.init(0.30, 64.5, 0.20);
        for (int i = 0; i < 40; i++) stairs.go(0.30 + (i % 20) * 0.01, 64.5, 0.20, 50);
        noConsequences("walking on a lower stair tread", stairs);
        Fixture phase = new Fixture();
        phase.world.put(0, 64, 0, Material.STONE, CUBE);
        phase.init(0.40, 64.1, 0.5);
        phase.go(0.45, 64.1, 0.5, 50);
        phase.go(0.50, 64.1, 0.5, 50);
        check("brief deep overlap alone does not flag phase", !phase.hasFlag(CheckType.PHASE));
        phase.go(0.55, 64.1, 0.5, 50);
        check("sustained deep movement inside stone still flags phase", phase.hasFlag(CheckType.PHASE));
        for (int i = 0; i < 20; i++) phase.go(i % 2 == 0 ? 0.50 : 0.55, 64.1, 0.5, 50);
        check("phase flags are rate-limited", phase.flagCount(CheckType.PHASE) <= 3);

        Fixture jitter = new Fixture();
        for (int x = 0; x < 16; x++) jitter.world.put(x, 63, 0, Material.STONE, CUBE);
        jitter.init(0.5, 64, 0.5);
        for (int i = 0; i < 40; i++) {
            Move move = jitter.go(0.5 + (i + 1) * 0.28, 64, 0.5, i % 4 < 2 ? 21 : 79);
            check("normal speed during packet jitter is not clamped " + i, !move.changedTo);
        }
        noConsequences("normal movement with two short and two long packet gaps", jitter);
        Fixture speed = new Fixture();
        for (int x = 0; x < 6; x++) speed.world.put(x, 63, 0, Material.STONE, CUBE);
        speed.init(0.5, 64, 0.5);
        speed.go(1.5, 64, 0.5, 50);
        Move clamped = speed.go(2.5, 64, 0.5, 50);
        check("sustained impossible ground speed still flags", speed.hasFlag(CheckType.SPEED));
        check("sustained impossible ground speed still clamps", clamped.changedTo && clamped.to.getX() < 2.5);
        Fixture speedNoAuthority = new Fixture(true, false);
        for (int x = 0; x < 6; x++) speedNoAuthority.world.put(x, 63, 0, Material.STONE, CUBE);
        speedNoAuthority.init(0.5, 64, 0.5);
        speedNoAuthority.go(1.5, 64, 0.5, 50);
        Move notClamped = speedNoAuthority.go(2.5, 64, 0.5, 50);
        check("hard-cap speed still flags without authority", speedNoAuthority.hasFlag(CheckType.SPEED));
        check("authority off does not clamp hard-cap speed", !notClamped.changedTo);
        Fixture moderateSpeed = new Fixture();
        for (int x = 0; x < 16; x++) moderateSpeed.world.put(x, 63, 0, Material.STONE, CUBE);
        moderateSpeed.init(0.5, 64, 0.5);
        for (int i = 0; i < 30; i++) moderateSpeed.go(0.5 + (i + 1) * 0.40, 64, 0.5, 50);
        check("sustained moderate speed excess still flags", moderateSpeed.hasFlag(CheckType.SPEED));
        check("moderate speed flags are rate-limited", moderateSpeed.flagCount(CheckType.SPEED) <= 3);
        Fixture disabled = new Fixture(false, true);
        disabled.init(0.5, 100, 0.5);
        disabled.go(1.5, 100, 0.5, 50);
        Move airborne = disabled.go(2.5, 100, 0.5, 50);
        check("disabled fly does not clamp airborne speed as fly", !airborne.changedTo && !disabled.hasFlag(CheckType.FLY));
    }

    private static void timerAndRotation() {
        Fixture rotation = grounded();
        for (int i = 0; i < 1000; i++) rotation.rotate(1);
        noConsequences("frequent looking around", rotation);

        Fixture normal = grounded();
        for (int i = 0; i < 100; i++) normal.go(0.5 + (i % 100) * 0.001, 64, 0.5, 50);
        noConsequences("normal 20 TPS positional updates", normal);

        Fixture burst = grounded();
        for (int i = 0; i < 100; i++) burst.go(0.5 + (i + 1) * 0.001, 64, 0.5, 1);
        for (int i = 0; i < 80; i++) burst.go(0.6 + (i + 1) * 0.001, 64, 0.5, 50);
        noConsequences("one position packet burst followed by normal movement", burst);

        Fixture spacedBursts = grounded();
        double x = 0.5;
        for (int cycle = 0; cycle < 2; cycle++) {
            for (int i = 0; i < 40; i++) { x += 0.001; spacedBursts.go(x, 64, 0.5, 1); }
            x += 0.001;
            spacedBursts.go(x, 64, 0.5, 1850);
        }
        noConsequences("short bursts separated by long gaps are averaged over actual window duration", spacedBursts);

        Fixture timer = grounded();
        for (int i = 0; i < 125; i++) timer.go(0.5 + (i + 1) * 0.001, 64, 0.5, 25);
        check("sustained 40 TPS positional updates still flag timer", timer.hasFlag(CheckType.TIMER));
        check("timer does not flag repeatedly in the same window", timer.flagCount(CheckType.TIMER) <= 2);
    }

    private static Fixture grounded() {
        Fixture f = new Fixture();
        f.world.put(0, 63, 0, Material.STONE, CUBE);
        f.init(0.5, 64, 0.5);
        return f;
    }

    private static void unavailableChunks() {
        Fixture f = new Fixture();
        f.init(15.3, 100, 0.5);
        f.trace().setAirSeconds(0.79); f.trace().setSpeedExcessStreak(2); f.trace().setJesusStreak(2);
        Bukkit.testRegionOwnership = (cx, cz) -> cx != 1;
        f.world.forbidden.add("1:0");
        try {
            Move move = f.go(15.9, 100, 0.5, 50);
            check("non-owned adjacent chunk does not clamp", !move.changedTo);
            check("non-owned chunk resets counters", f.trace().airSeconds() == 0 && f.trace().speedExcessStreak() == 0
                    && f.trace().jesusStreak() == 0);
            check("no block read in non-owned chunk", f.world.forbiddenReads == 0);
            noConsequences("Folia region boundary", f);
        } finally {
            Bukkit.testRegionOwnership = null;
        }
        Fixture unloaded = new Fixture();
        unloaded.init(15.3, 100, 0.5);
        unloaded.world.unloaded.add("1:0");
        unloaded.world.forbidden.add("1:0");
        unloaded.trace().setAirSeconds(0.79);
        unloaded.go(15.9, 100, 0.5, 50);
        check("unloaded adjacent chunk resets flight", unloaded.trace().airSeconds() == 0);
        check("no block read or implicit load for unloaded chunk", unloaded.world.forbiddenReads == 0);
        noConsequences("unloaded chunk boundary", unloaded);
    }

    private static void noConsequences(String name, Fixture f) {
        check(name + " has no flags", f.store.flags.isEmpty());
        check(name + " has no pull", f.player.velocityWrites == 0);
        check(name + " has no kicks", f.player.kicks == 0);
        check(name + " has no ban", f.store.bans == 0);
    }

    private static void check(String name, boolean ok) {
        if (ok) passed++; else { failed++; System.out.println("FAIL " + name); }
    }
    private static BoundingBox box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new BoundingBox(x1, y1, z1, x2, y2, z2);
    }
    private static String key(int x, int y, int z) { return x + ":" + y + ":" + z; }
    private static String chunk(int x, int z) { return x + ":" + z; }

    private static final class Fixture {
        final TestWorld world = new TestWorld();
        final TestPlayer player = new TestPlayer(world.proxy);
        final TestStore store = new TestStore();
        final YamlConfiguration yaml = new YamlConfiguration();
        final VeltryxConfig config;
        final AntiCheatService service;
        final AntiCheatListener listener;
        long nanos = 1_000_000_000L;
        Fixture() { this(true, true); }
        Fixture(boolean flyEnabled, boolean authority) {
            yaml.set("anticheat.join-grace-seconds", 0);
            yaml.set("anticheat.xray.obfuscation", false);
            yaml.set("anticheat.server-authority", authority);
            yaml.set("anticheat.checks.movement.fly.enabled", flyEnabled);
            for (CheckType type : CheckType.values()) {
                String path = "anticheat.checks." + type.configPath();
                yaml.set(path + ".alert-vl", 1_000_000.0);
                yaml.set(path + ".kick-vl", 1_000_000.0);
                yaml.set(path + ".ban-vl", 1_000_000.0);
            }
            config = new VeltryxConfig(yaml);
            service = new AntiCheatService(store.proxy, null, config, null);
            listener = new AntiCheatListener(service, config, new OreObfuscator(config), () -> nanos);
        }
        Location at(double x, double y, double z) { return new Location(world.proxy, x, y, z); }
        MovementEnvironment.Sample sample(double x, double y, double z) { return MovementEnvironment.sample(player.proxy, at(x, y, z)); }
        PlayerTrace trace() { return service.trace(player.proxy); }
        void init(double x, double y, double z) {
            player.location = at(x, y, z);
            listener.onMove(new Move(player.proxy, player.location, player.location));
        }
        Move go(double x, double y, double z, long millis) {
            return move(at(x, y, z), millis);
        }
        Move rotate(long millis) {
            Location p = player.location;
            return move(new Location(world.proxy, p.getX(), p.getY(), p.getZ(), 50, 10), millis);
        }
        Move move(Location to, long millis) {
            nanos += millis * 1_000_000L;
            Move event = new Move(player.proxy, player.location, to);
            listener.onMove(event);
            if (!event.cancelled) player.location = event.to;
            return event;
        }
        boolean hasFlag(CheckType type) { return flagCount(type) > 0; }
        long flagCount(CheckType type) { return store.flags.stream().filter(f -> f.check().equals(type.display())).count(); }
    }

    private static final class Move extends PlayerMoveEvent {
        final Player player;
        final Location from;
        Location to;
        boolean cancelled, changedTo;
        Move(Player player, Location from, Location to) { this.player = player; this.from = from; this.to = to; }
        @Override public Player getPlayer() { return player; }
        @Override public Location getFrom() { return from; }
        @Override public Location getTo() { return to; }
        @Override public void setTo(Location to) { this.to = to; changedTo = true; }
        @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
        @Override public boolean isCancelled() { return cancelled; }
    }

    private static final class TestStore implements InvocationHandler {
        final List<FlagEntry> flags = new ArrayList<>();
        int bans;
        final DataStore proxy = (DataStore) Proxy.newProxyInstance(DataStore.class.getClassLoader(), new Class<?>[]{DataStore.class}, this);
        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getName().equals("saveFlag")) flags.add((FlagEntry) args[0]);
            if (method.getName().equals("saveBan")) bans++;
            return defaultValue(method.getReturnType());
        }
    }

    private static final class TestPlayer implements InvocationHandler {
        final UUID id = UUID.randomUUID();
        final World world;
        final Player proxy = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, this);
        final List<PotionEffect> effects = new ArrayList<>();
        Location location;
        double width = 0.6, height = 1.8;
        int ping, kicks, velocityWrites;
        boolean vehicle, gliding, flying, riptiding, swimming, dead, claimedOnGround, inWater;
        Vector velocity = new Vector(0, 0, 0);
        TestPlayer(World world) { this.world = world; }
        @Override public Object invoke(Object object, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> "movement-test";
                case "getGameMode" -> GameMode.SURVIVAL;
                case "getPing" -> ping;
                case "getWidth" -> width;
                case "getHeight" -> height;
                case "getWorld" -> world;
                case "getLocation" -> location;
                case "getActivePotionEffects" -> effects;
                case "isInsideVehicle" -> vehicle;
                case "isGliding" -> gliding;
                case "isFlying" -> flying;
                case "isRiptiding" -> riptiding;
                case "isSwimming" -> swimming;
                case "isInWater" -> inWater;
                case "isDead" -> dead;
                case "isOnGround" -> claimedOnGround;
                case "getFallDistance" -> 0.0f;
                case "getVelocity" -> velocity;
                case "setVelocity" -> { velocity = (Vector) args[0]; velocityWrites++; yield null; }
                case "kick" -> { kicks++; yield null; }
                case "equals" -> object == args[0];
                case "hashCode" -> System.identityHashCode(object);
                case "toString" -> "TestPlayer";
                default -> defaultValue(method.getReturnType());
            };
        }
    }

    private static final class TestWorld implements InvocationHandler {
        final Map<String, TestBlock> blocks = new HashMap<>();
        final Set<String> unloaded = new HashSet<>(), forbidden = new HashSet<>();
        int forbiddenReads;
        final World proxy = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class}, this);
        void put(int x, int y, int z, Material type, BoundingBox... boxes) {
            blocks.put(key(x, y, z), new TestBlock(proxy, x, y, z, type, List.of(boxes)));
        }
        @Override public Object invoke(Object object, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getBlockAt" -> {
                    int x = (int) args[0], y = (int) args[1], z = (int) args[2];
                    if (forbidden.contains(chunk(x >> 4, z >> 4))) {
                        forbiddenReads++;
                        throw new AssertionError("Read forbidden chunk " + chunk(x >> 4, z >> 4));
                    }
                    yield blocks.getOrDefault(key(x, y, z), new TestBlock(proxy, x, y, z, Material.AIR, List.of()));
                }
                case "isChunkLoaded" -> !unloaded.contains(chunk((int) args[0], (int) args[1]));
                case "getMinHeight" -> -64;
                case "getMaxHeight" -> 320;
                case "getName" -> "test-world";
                case "equals" -> object == args[0];
                case "hashCode" -> System.identityHashCode(object);
                case "toString" -> "TestWorld";
                default -> defaultValue(method.getReturnType());
            };
        }
    }

    private static final class TestBlock implements Block {
        final World world;
        final int x, y, z;
        final Material type;
        final List<BoundingBox> boxes;
        TestBlock(World world, int x, int y, int z, Material type, List<BoundingBox> boxes) {
            this.world = world; this.x = x; this.y = y; this.z = z; this.type = type; this.boxes = boxes;
        }
        @Override public Material getType() { return type; }
        @Override public VoxelShape getCollisionShape() {
            return new VoxelShape() {
                @Override public Collection<BoundingBox> getBoundingBoxes() { return boxes; }
                @Override public boolean overlaps(BoundingBox other) {
                    return boxes.stream().anyMatch(b -> b.getMaxX() > other.getMinX() && b.getMinX() < other.getMaxX()
                            && b.getMaxY() > other.getMinY() && b.getMinY() < other.getMaxY()
                            && b.getMaxZ() > other.getMinZ() && b.getMinZ() < other.getMaxZ());
                }
            };
        }
        @Override public Block getRelative(BlockFace face) { return null; }
        @Override public Location getLocation() { return new Location(world, x, y, z); }
        @Override public BlockData getBlockData() { return null; }
        @Override public BlockState getState() { return null; }
        @Override public void setType(Material type) { throw new UnsupportedOperationException(); }
        @Override public boolean breakNaturally() { return false; }
        @Override public boolean breakNaturally(ItemStack tool, boolean triggerEffect) { return false; }
        @Override public World getWorld() { return world; }
        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public int getZ() { return z; }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        return null;
    }
}
