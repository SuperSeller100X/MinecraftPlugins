package dev.superseller.shardtools.smoke;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import dev.superseller.shardtools.expiry.ExpiryMath;
import dev.superseller.shardtools.shop.PriceBook;
import dev.superseller.shardtools.tools.AreaPlane;
import dev.superseller.shardtools.tools.TreeFeller;
import dev.superseller.shardtools.util.Numbers;
import dev.superseller.shardtools.util.TimeWords;

/**
 * Offline smoke tests for the pure logic of ShardTools. No Bukkit classes
 * involved - runs on a plain JVM. Executed by CI and by build.sh.
 */
public final class SmokeTest {

    private static int passed;
    private static final StringBuilder failures = new StringBuilder();

    public static void main(String[] args) {
        testNumbers();
        testTimeWords();
        testExpiryMath();
        testAreaPlane();
        testTreeFeller();
        testPriceBook();

        if (failures.length() > 0) {
            System.err.println("FAILURES:\n" + failures);
            System.exit(1);
        }
        System.out.println("All " + passed + " smoke tests passed.");
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failures.append("  - ").append(name).append('\n');
        }
    }

    private static void checkEquals(String name, Object expected, Object actual) {
        check(name + " (expected " + expected + ", got " + actual + ")",
                expected == null ? actual == null : expected.equals(actual));
    }

    private static void testNumbers() {
        checkEquals("parse plain", 1500L, Numbers.parse("1500"));
        checkEquals("parse 2.5k", 2500L, Numbers.parse("2.5k"));
        checkEquals("parse 10K", 10000L, Numbers.parse("10K"));
        checkEquals("parse 1.2m", 1200000L, Numbers.parse("1.2m"));
        checkEquals("parse 3b", 3000000000L, Numbers.parse("3b"));
        check("parse negative rejected", Numbers.parse("-5") == null);
        check("parse garbage rejected", Numbers.parse("abc") == null);
        check("parse empty rejected", Numbers.parse("") == null);
        checkEquals("format grouping", "1,234,567", Numbers.format(1234567L));
        checkEquals("format zero", "0", Numbers.format(0L));
        checkEquals("format negative", "-4,500", Numbers.format(-4500L));
        checkEquals("compact million", "1.5M", Numbers.compact(1500000L));
        checkEquals("compact small", "300", Numbers.compact(300L));
        checkEquals("compact ten-k", "10k", Numbers.compact(10000L));
    }

    private static void testTimeWords() {
        checkEquals("24h", "23h 59m", TimeWords.format(23 * 3600_000L + 59 * 60_000L + 12_000L));
        checkEquals("90s", "1m 30s", TimeWords.format(90_000L));
        checkEquals("3s", "3s", TimeWords.format(3_000L));
        checkEquals("zero", "0s", TimeWords.format(0L));
        checkEquals("negative", "0s", TimeWords.format(-5L));
        checkEquals("days", "3d 4h", TimeWords.format((3 * 86400L + 4 * 3600L + 5L) * 1000L));
        checkEquals("minutesOf", 59L, TimeWords.minutesOf(59 * 60_000L + 59_000L));
        checkEquals("minutesOf clamps", 0L, TimeWords.minutesOf(-100L));
    }

    private static void testExpiryMath() {
        long day = 86_400_000L;
        long created = 1_000_000L;
        checkEquals("expiresAt", created + day, ExpiryMath.expiresAt(created, day));
        checkEquals("permanent expiresAt MAX", Long.MAX_VALUE, ExpiryMath.expiresAt(created, 0L));
        checkEquals("remaining half", day / 2, ExpiryMath.remaining(created, day, created + day / 2));
        checkEquals("remaining clamped", 0L, ExpiryMath.remaining(created, day, created + day * 2));
        checkEquals("remaining permanent MAX", Long.MAX_VALUE, ExpiryMath.remaining(created, 0L, created));
        check("expired", ExpiryMath.expired(created, day, created + day));
        check("not expired yet", !ExpiryMath.expired(created, day, created + day - 1));
        check("never expired when permanent", !ExpiryMath.expired(created, 0L, created + Long.MAX_VALUE / 2));

        long[] warn = {60, 10, 1};
        check("no warning above 60m", ExpiryMath.dueWarning(61 * 60_000L, warn, 0) == -1);
        check("warning at 60m", ExpiryMath.dueWarning(60 * 60_000L, warn, 0) == 0);
        check("warning bit respected", ExpiryMath.dueWarning(60 * 60_000L, warn, 1) == -1);
        check("10m warning after 60m warned", ExpiryMath.dueWarning(10 * 60_000L, warn, 1) == 1);
        check("warning 10m slot", ExpiryMath.dueWarning(10 * 60_000L, warn, 1) == 1);
        check("warning 1m slot", ExpiryMath.dueWarning(30_000L, warn, 3) == 2);
        check("all warned", ExpiryMath.dueWarning(30_000L, warn, 7) == -1);
    }

    private static void testAreaPlane() {
        List<int[]> down = AreaPlane.offsets(0.0, -1.0, 0.0, 1);
        checkEquals("down = 8 offsets", 8, down.size());
        boolean allHorizontal = true;
        for (int[] offset : down) {
            if (offset[1] != 0) {
                allHorizontal = false;
            }
        }
        check("down plane varies x/z only", allHorizontal);

        List<int[]> north = AreaPlane.offsets(0.0, 0.0, -1.0, 1);
        boolean allXY = true;
        for (int[] offset : north) {
            if (offset[2] != 0) {
                allXY = false;
            }
        }
        check("north plane varies x/y only", allXY);
        checkEquals("north count", 8, north.size());

        List<int[]> east = AreaPlane.offsets(1.0, 0.0, 0.0, 2);
        checkEquals("radius 2 = 24 offsets", 24, east.size());
        boolean allYZ = true;
        for (int[] offset : east) {
            if (offset[0] != 0) {
                allYZ = false;
            }
        }
        check("east plane varies y/z only", allYZ);
    }

    private static void testTreeFeller() {
        // Trunk 5 high with a 2-block branch at the top.
        Map<String, String> world = new java.util.HashMap<>();
        for (int y = 64; y <= 68; y++) {
            world.put("0," + y + ",0", "OAK_LOG");
        }
        world.put("1,68,0", "OAK_LOG");
        world.put("2,68,0", "OAK_LOG");
        world.put("0,69,0", "OAK_LEAVES");
        world.put("5,70,5", "BIRCH_LOG"); // not connected

        TreeFeller.Grid grid = (x, y, z) -> world.get(x + "," + y + "," + z);
        Set<String> logNames = new HashSet<>(List.of("OAK_LOG", "BIRCH_LOG"));

        List<int[]> logs = TreeFeller.collect(0, 64, 0, "OAK_LOG", grid,
                logNames::contains, true, 256);
        checkEquals("whole tree collected", 6, logs.size());

        List<int[]> capped = TreeFeller.collect(0, 64, 0, "OAK_LOG", grid,
                logNames::contains, true, 3);
        checkEquals("max block cap", 2, capped.size());

        Map<String, String> mixed = new java.util.HashMap<>();
        for (int y = 64; y <= 66; y++) {
            mixed.put("0," + y + ",0", "OAK_LOG");
        }
        mixed.put("1,66,0", "BIRCH_LOG");
        List<int[]> sameOnly = TreeFeller.collect(0, 64, 0, "OAK_LOG",
                (x, y, z) -> mixed.get(x + "," + y + "," + z),
                logNames::contains, true, 256);
        checkEquals("same material only", 2, sameOnly.size());
        List<int[]> mixedFell = TreeFeller.collect(0, 64, 0, "OAK_LOG",
                (x, y, z) -> mixed.get(x + "," + y + "," + z),
                logNames::contains, false, 256);
        checkEquals("mixed materials allowed", 3, mixedFell.size());

        Map<String, String> leavesOnly = new java.util.HashMap<>();
        for (int x = 0; x <= 2; x++) {
            leavesOnly.put(x + ",64,0", "OAK_LEAVES");
        }
        List<int[]> fromLeaves = TreeFeller.collect(0, 64, 0, "OAK_LEAVES",
                (x, y, z) -> leavesOnly.get(x + "," + y + "," + z),
                logNames::contains, false, 256);
        checkEquals("leaves not logs", 0, fromLeaves.size());

        // Whole-tree behaviour: leaves adjacent to felled logs are collected.
        Map<String, String> withLeaves = new java.util.HashMap<>();
        for (int y = 64; y <= 66; y++) {
            withLeaves.put("0," + y + ",0", "OAK_LOG");
        }
        withLeaves.put("1,66,0", "OAK_LEAVES");
        withLeaves.put("-1,65,0", "OAK_LEAVES");
        withLeaves.put("4,64,4", "OAK_LEAVES"); // isolated, not adjacent
        java.util.function.Predicate<String> leafCheck = n -> n.endsWith("_LEAVES");
        List<int[]> trunk = List.of(new int[]{0, 64, 0}, new int[]{0, 65, 0}, new int[]{0, 66, 0});
        List<int[]> treeLeaves = TreeFeller.collectLeaves(trunk,
                (x, y, z) -> withLeaves.get(x + "," + y + "," + z), leafCheck::test, 64);
        checkEquals("adjacent leaves collected", 2, treeLeaves.size());
        List<int[]> cappedLeaves = TreeFeller.collectLeaves(trunk,
                (x, y, z) -> withLeaves.get(x + "," + y + "," + z), leafCheck::test, 1);
        checkEquals("leaf cap respected", 1, cappedLeaves.size());
        List<int[]> noLeaves = TreeFeller.collectLeaves(List.of(),
                (x, y, z) -> withLeaves.get(x + "," + y + "," + z), leafCheck::test, 64);
        checkEquals("no logs no leaves", 0, noLeaves.size());
    }

    private static void testPriceBook() {
        Map<String, Long> defaults = new java.util.HashMap<>();
        defaults.put("shard_pickaxe_fortune", 3000L);
        defaults.put("haste_potion", 6000L);
        Map<String, Long> overrides = new java.util.HashMap<>();
        overrides.put("haste_potion", 5500L);

        PriceBook book = new PriceBook(defaults, overrides);
        checkEquals("default price", 3000L, book.price("shard_pickaxe_fortune"));
        checkEquals("override wins", 5500L, book.price("haste_potion"));
        check("unknown item null", book.price("nope") == null);
        check("override flagged", book.isOverridden("haste_potion"));
        check("no override flag", !book.isOverridden("shard_pickaxe_fortune"));

        book.setPrice("shard_pickaxe_fortune", 2500L);
        checkEquals("setPrice applied", 2500L, book.price("shard_pickaxe_fortune"));
        book.reset("shard_pickaxe_fortune");
        checkEquals("reset to default", 3000L, book.price("shard_pickaxe_fortune"));

        book.reload(Map.of("haste_potion", 6000L), Map.of());
        checkEquals("reload clears overrides", 6000L, book.price("haste_potion"));
    }
}
