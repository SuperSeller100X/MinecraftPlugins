package dev.superseller.veltryx.shop;

import dev.superseller.veltryx.util.Money;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The shop price table, parsed from {@code shop.yml} — pure Java (no Bukkit)
 * so the whole pricing model is unit-tested in the smoke suite.
 *
 * <p>File format (one line per item):
 * <pre>
 * category-id:
 *   display: "Category Name"
 *   icon: DIAMOND
 *   items:
 *     MATERIAL: { buy: 8.00, sell: 5.20 }
 * </pre>
 *
 * <p>Anti-arbitrage is a property of the shipped table: gathered resources
 * sell at ~65% of buy, crafted items at ~50% of buy and cost barely more than
 * their materials — so buy-materials → craft → sell always loses money. The
 * smoke suite verifies this against the real table and a list of dangerous
 * recipes.
 */
public final class PriceTable {

    /** One shop item. Prices are stored in cents. */
    public record Entry(String category, String material, long buyCents, long sellCents) {
    }

    /** One GUI category. */
    public record Category(String id, String display, String icon, List<Entry> items) {
    }

    private static final Pattern CATEGORY = Pattern.compile("^([a-z][a-z0-9_]*):$");
    private static final Pattern DISPLAY = Pattern.compile("^  display: \"(.*)\"$");
    private static final Pattern ICON = Pattern.compile("^  icon: ([A-Z][A-Z0-9_]*)$");
    private static final Pattern ITEMS = Pattern.compile("^  items:$");
    private static final Pattern ENTRY = Pattern.compile(
            "^    ([A-Z][A-Z0-9_]*): \\{ buy: ([0-9]+\\.[0-9]{2}), sell: ([0-9]+\\.[0-9]{2}) }$");

    private final Map<String, Entry> byMaterial = new LinkedHashMap<>();
    private final List<Category> categories = new ArrayList<>();

    private PriceTable() {
    }

    /** Parses a shop.yml document. Unknown/invalid lines are ignored. */
    public static PriceTable parse(String text) {
        PriceTable table = new PriceTable();
        String currentId = null;
        String currentDisplay = null;
        String currentIcon = "CHEST";
        List<Entry> currentItems = null;
        for (String line : text.split("\n", -1)) {
            Matcher category = CATEGORY.matcher(line);
            if (category.matches()) {
                table.flushCategory(currentId, currentDisplay, currentIcon, currentItems);
                currentId = category.group(1);
                currentDisplay = null;
                currentIcon = "CHEST";
                currentItems = new ArrayList<>();
                continue;
            }
            Matcher display = DISPLAY.matcher(line);
            if (display.matches()) {
                currentDisplay = display.group(1);
                continue;
            }
            Matcher icon = ICON.matcher(line);
            if (icon.matches()) {
                currentIcon = icon.group(1);
                continue;
            }
            if (ITEMS.matcher(line).matches()) {
                continue;
            }
            Matcher entry = ENTRY.matcher(line);
            if (entry.matches() && currentItems != null) {
                String material = entry.group(1);
                if (table.byMaterial.containsKey(material)) {
                    continue;
                }
                long buy = Money.parse(entry.group(2));
                long sell = Money.parse(entry.group(3));
                Entry item = new Entry(currentId, material, buy, sell);
                table.byMaterial.put(material, item);
                currentItems.add(item);
            }
        }
        table.flushCategory(currentId, currentDisplay, currentIcon, currentItems);
        return table;
    }

    private void flushCategory(String id, String display, String icon, List<Entry> items) {
        if (id == null || items == null) {
            return;
        }
        categories.add(new Category(id, display != null ? display : id, icon, Collections.unmodifiableList(items)));
    }

    /** Entry for a material name, or {@code null} when the shop does not trade it. */
    public Entry get(String material) {
        return byMaterial.get(material);
    }

    /** Buy price in cents, or -1 when unknown. */
    public long buyCents(String material) {
        Entry entry = byMaterial.get(material);
        return entry == null ? -1 : entry.buyCents();
    }

    /** Sell price in cents, or -1 when unknown. */
    public long sellCents(String material) {
        Entry entry = byMaterial.get(material);
        return entry == null ? -1 : entry.sellCents();
    }

    public List<Category> categories() {
        return Collections.unmodifiableList(categories);
    }

    public int size() {
        return byMaterial.size();
    }

    /** "POLISHED_BLACKSTONE_BRICKS" → "Polished Blackstone Bricks". */
    public static String displayName(String material) {
        String[] words = material.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
