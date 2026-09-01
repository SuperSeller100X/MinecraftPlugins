import dev.superseller.xpbank.util.ExperienceUtil;
import dev.superseller.xpbank.util.Numbers;

/**
 * Tiny dependency-free smoke test for the pure parts of XPBank. Compile the
 * util classes and this file, then run it. This mirrors the offline harness
 * used by other plugins in the repo and requires no server or network.
 *
 * <pre>
 *   javac -d smoke-out src/main/java/dev/superseller/xpbank/util/ExperienceUtil.java \
 *                      src/main/java/dev/superseller/xpbank/util/Numbers.java \
 *                      smoke/SmokeTest.java
 *   java -cp smoke-out SmokeTest
 * </pre>
 *
 * <p>Note: ExperienceUtil imports org.bukkit.entity.Player for its live-player
 * helpers, so a full compile of that class needs paper-api on the classpath;
 * the authoritative checks live in the JUnit suite run by {@code mvn verify}.
 */
public final class SmokeTest {

    private static int failures = 0;

    public static void main(String[] args) {
        check("cum L0", ExperienceUtil.xpAtLevel(0) == 0);
        check("cum L30", ExperienceUtil.xpAtLevel(30) == 1395);
        check("cum L31", ExperienceUtil.xpAtLevel(31) == 1507);
        check("next 15", ExperienceUtil.xpToNext(15) == 37);
        check("next 16", ExperienceUtil.xpToNext(16) == 42);

        check("parse 1k", Numbers.parseAmount("1k") == 1000L);
        check("parse 2.5m", Numbers.parseAmount("2.5m") == 2_500_000L);
        check("parse all", Numbers.parseAmount("all") == Numbers.ALL);
        check("parse bad", Numbers.parseAmount("nope") == Numbers.INVALID);
        check("grouped", Numbers.grouped(1234567L).equals("1,234,567"));
        check("compact", Numbers.compact(1_234_567L).equals("1.23M"));

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
}
