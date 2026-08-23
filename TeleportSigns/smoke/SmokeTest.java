package dev.superseller.teleportsigns.smoke;

import java.util.Map;
import java.util.UUID;

import dev.superseller.teleportsigns.command.DestinationParser;
import dev.superseller.teleportsigns.model.SignCodec;
import dev.superseller.teleportsigns.model.SignKey;
import dev.superseller.teleportsigns.model.TeleportSign;
import dev.superseller.teleportsigns.model.WarpDestination;
import dev.superseller.teleportsigns.service.SafetyChecker;
import dev.superseller.teleportsigns.util.Numbers;
import dev.superseller.teleportsigns.util.Placeholders;

/**
 * Offline smoke test for the Bukkit-free parts of TeleportSigns.
 *
 * <pre>
 * javac -d smoke-classes \
 *   TeleportSigns/smoke/SmokeTest.java \
 *   TeleportSigns/src/main/java/dev/superseller/teleportsigns/util/*.java \
 *   TeleportSigns/src/main/java/dev/superseller/teleportsigns/command/DestinationParser.java \
 *   TeleportSigns/src/main/java/dev/superseller/teleportsigns/model/*.java \
 *   TeleportSigns/src/main/java/dev/superseller/teleportsigns/service/SafetyChecker.java
 * java -cp smoke-classes dev.superseller.teleportsigns.smoke.SmokeTest
 * </pre>
 */
public final class SmokeTest {

    private static int failures = 0;

    public static void main(String[] args) {
        testNumbers();
        testPlaceholders();
        testParser();
        testCodec();
        testSafety();
        if (failures > 0) {
            System.out.println("SMOKE TEST FAILED: " + failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("SMOKE TEST OK");
    }

    private static void check(String name, boolean condition) {
        System.out.println((condition ? "  ok  " : "  FAIL ") + name);
        if (!condition) {
            failures++;
        }
    }

    private static void testNumbers() {
        System.out.println("Numbers:");
        check("pretty int", "10".equals(Numbers.pretty(10.0d)));
        check("pretty decimal", "10.50".equals(Numbers.pretty(10.5d)));
        check("parse 100", Numbers.parseDouble("100") != null && Numbers.parseDouble("100").doubleValue() == 100.0d);
        check("parse ~ rejected", Numbers.parseDouble("~") == null);
        check("parse int 4", Integer.valueOf(4).equals(Numbers.parseInt("4")));
        check("parse int 4.5 rejected", Numbers.parseInt("4.5") == null);
    }

    private static void testPlaceholders() {
        System.out.println("Placeholders:");
        check("replace", "go to world".equals(Placeholders.apply("go to {world}", Map.of("world", "world"))));
        check("missing stays", "{x} stays".equals(Placeholders.apply("{x} stays", Map.of("world", "a"))));
    }

    private static void testParser() {
        System.out.println("DestinationParser:");
        DestinationParser.Result xyz = DestinationParser.parse(new String[] {"100", "64", "-20"});
        check("xyz ok", xyz.ok && !xyz.hasWorld() && xyz.x.value == 100 && xyz.y.value == 64 && xyz.z.value == -20);
        DestinationParser.Result world = DestinationParser.parse(new String[] {"world_nether", "1", "2", "3"});
        check("world xyz", world.ok && "world_nether".equals(world.world) && world.x.value == 1);
        DestinationParser.Result rot = DestinationParser.parse(new String[] {"1", "2", "3", "90", "10"});
        check("yaw pitch", rot.ok && rot.hasRotation() && rot.yaw.floatValue() == 90.0f && rot.pitch.floatValue() == 10.0f);
        DestinationParser.Result yawOnly = DestinationParser.parse(new String[] {"1", "2", "3", "180"});
        check("yaw defaults pitch 0", yawOnly.ok && yawOnly.pitch.floatValue() == 0.0f);
        DestinationParser.Result rel = DestinationParser.parse(new String[] {"~", "~1", "~-2"});
        check("relative", rel.ok && rel.x.relative && rel.x.resolve(10) == 10 && rel.y.resolve(64) == 65 && rel.z.resolve(0) == -2);
        check("~ token", DestinationParser.isCoordToken("~10"));
        check("world not coord", !DestinationParser.isCoordToken("world"));
        check("too few", !DestinationParser.parse(new String[] {"1", "2"}).ok);
        check("bad number", "invalid-number".equals(DestinationParser.parse(new String[] {"a", "2", "3"}).errorKey)
                || "usage-set".equals(DestinationParser.parse(new String[] {"a", "2", "3"}).errorKey)
                || DestinationParser.parse(new String[] {"nope"}).errorKey != null);
        DestinationParser.Result namedBad = DestinationParser.parse(new String[] {"world", "nope", "2", "3"});
        check("bad coord after world", !namedBad.ok && "invalid-number".equals(namedBad.errorKey));
    }

    private static void testCodec() {
        System.out.println("SignCodec:");
        SignKey key = new SignKey("world", 10, 64, -5);
        WarpDestination dest = new WarpDestination("world_the_end", 1.5d, 80.0d, -9.25d, 90.0f, 12.5f, true);
        UUID creator = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        TeleportSign original = new TeleportSign(key, dest, 25.5d, creator, 1_700_000_000_000L);
        String encoded = SignCodec.encode(original);
        TeleportSign decoded = SignCodec.decode(key, encoded);
        check("round-trip dest world", decoded != null && "world_the_end".equals(decoded.destination().worldName()));
        check("round-trip x", decoded != null && decoded.destination().x() == 1.5d);
        check("round-trip rot", decoded != null && decoded.destination().hasRotation()
                && decoded.destination().yaw() == 90.0f);
        check("round-trip cost", decoded != null && decoded.cost() == 25.5d);
        check("round-trip creator", decoded != null && creator.equals(decoded.creator()));
        check("escape semicolon world", "a;b".equals(SignCodec.unescape(SignCodec.escape("a;b"))));
        SignKey other = new SignKey("WORLD", 10, 64, -5);
        check("sign key case-insensitive", key.equals(other) && key.hashCode() == other.hashCode());
        check("bad payload", SignCodec.decode(key, "nope") == null);
    }

    private static void testSafety() {
        System.out.println("SafetyChecker:");
        check("air", SafetyChecker.isAir("CAVE_AIR"));
        check("lava", SafetyChecker.isLava("LAVA"));
        check("fire", SafetyChecker.isFire("SOUL_FIRE"));
        check("void", "void".equals(SafetyChecker.evaluate("AIR", "AIR", "STONE", false, true, true, true, true, true)));
        check("lava feet", "lava".equals(SafetyChecker.evaluate("LAVA", "AIR", "STONE", true, true, true, true, true, true)));
        check("fire", "fire".equals(SafetyChecker.evaluate("FIRE", "AIR", "STONE", true, true, true, true, true, true)));
        check("solid head", "solid".equals(SafetyChecker.evaluate("AIR", "STONE", "STONE", true, true, true, true, true, true)));
        check("no floor", "floor".equals(SafetyChecker.evaluate("AIR", "AIR", "AIR", true, false, true, true, true, true)));
        check("safe", SafetyChecker.evaluate("AIR", "AIR", "STONE", true, true, true, true, true, true) == null);
    }
}
