package dev.superseller.gifty.smoke;

import dev.superseller.gifty.model.Delivery;
import dev.superseller.gifty.model.SerialItem;
import dev.superseller.gifty.util.Colors;
import dev.superseller.gifty.util.MiniYaml;
import dev.superseller.gifty.util.Numbers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tiny offline smoke test that exercises the pure (server-independent) parts
 * of Gifty: YAML round-trips, delivery serialization, colors and numbers.
 */
public final class SmokeTest {

    private static int failures = 0;

    public static void main(String[] args) {
        testColors();
        testNumbers();
        testMiniYaml();
        testDeliveryRoundTrip();
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

    private static void testColors() {
        System.out.println("colors:");
        check("hex", Colors.parse("&#ff8800hi").equals("\u00a7x\u00a7f\u00a7f\u00a78\u00a78\u00a70\u00a70hi"));
        check("amp", Colors.parse("&aGreen").equals("\u00a7aGreen"));
        check("escape", Colors.parse("&&amp").equals("&amp"));
        check("strip", Colors.strip("\u00a7a\u00a7lBold").equals("Bold"));
    }

    private static void testNumbers() {
        System.out.println("numbers:");
        check("1.5K", Numbers.format(1500).equals("1.5K"));
        check("2.1M", Numbers.format(2_100_000).equals("2.1M"));
        check("1B", Numbers.format(1_000_000_000).equals("1B"));
        check("plain", Numbers.format(42).equals("42"));
        check("duration", Numbers.duration(65).equals("1m 5s"));
    }

    private static void testMiniYaml() {
        System.out.println("mini-yaml:");
        String text = "# comment\n"
                + "name: 'Gifty'\n"
                + "count: 42\n"
                + "price: 12.5\n"
                + "enabled: true\n"
                + "nested:\n"
                + "  key: value\n"
                + "items:\n"
                + "  - type: DIAMOND\n"
                + "    amount: 64\n"
                + "  - note\n"
                + "  - key: 'with: colon & hash # here'\n";
        Map<String, Object> map = MiniYaml.parse(text);
        check("scalar string", "Gifty".equals(map.get("name")));
        check("scalar int", Long.valueOf(42).equals(map.get("count")));
        check("scalar double", Double.valueOf(12.5).equals(map.get("price")));
        check("scalar bool", Boolean.TRUE.equals(map.get("enabled")));
        check("nested", map.get("nested") instanceof Map);
        check("list size", map.get("items") instanceof List && ((List<?>) map.get("items")).size() == 3);
        Object first = ((List<?>) map.get("items")).get(0);
        check("list map item", first instanceof Map && "DIAMOND".equals(((Map<?, ?>) first).get("type")));
        Object third = ((List<?>) map.get("items")).get(2);
        check("quoted colon", "with: colon & hash # here".equals(((Map<?, ?>) third).get("key")));

        String dumped = MiniYaml.dump(map);
        Map<String, Object> reparsed = MiniYaml.parse(dumped);
        check("roundtrip", reparsed.equals(map));
        check("newline roundtrip", "a\nb".equals(MiniYaml.parse(MiniYaml.dump(single("x", "a\nb"))).get("x")));
    }

    private static Map<String, Object> single(String k, Object v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }

    private static void testDeliveryRoundTrip() {
        System.out.println("delivery:");
        SerialItem diamond = new SerialItem("DIAMOND", 64, "\u00a7bShiny", Arrays.asList("line1", "line2"),
                new ArrayList<String>(), new LinkedHashMap<String, Integer>(), null);
        Delivery d = new Delivery(7L, UUID.randomUUID(), "Steve", System.currentTimeMillis(),
                "Have fun!", 1234.5, Arrays.asList(diamond));
        Map<String, Object> map = d.toMap();
        Delivery back = Delivery.fromMap(map);
        check("id", back.id() == 7L);
        check("from", "Steve".equals(back.fromName()));
        check("money", back.money() == 1234.5);
        check("message", "Have fun!".equals(back.message()));
        check("items", back.items().size() == 1 && back.totalItems() == 64);
        check("item fields", "DIAMOND".equals(back.items().get(0).material())
                && "\u00a7bShiny".equals(back.items().get(0).displayName())
                && back.items().get(0).lore().size() == 2);
        // whole-file roundtrip through the real storage format
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("deliveries", Arrays.asList((Object) map));
        Map<String, Object> reparsed = MiniYaml.parse(MiniYaml.dump(file));
        check("file roundtrip", reparsed.equals(file));
    }
}
