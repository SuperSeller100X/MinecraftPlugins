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
 * <p>Configured base sells are capped at half the cheapest cost found in the
 * bundled recipe/transformation model. This reduces buy-transform-sell
 * arbitrage, but cannot cover all farms, trades, plugins, or game mechanics.
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
        return parse(text, "", name -> true);
    }

    /** Parse, filter unavailable materials, and cap sell prices by cheapest recipes. */
    public static PriceTable parse(String text, String recipes, java.util.function.Predicate<String> available) {
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
                String material = SurvivalItems.canonical(entry.group(1));
                if (!SurvivalItems.allowed(material) || !available.test(material)
                        || table.byMaterial.containsKey(material)) {
                    continue;
                }
                long buy = Money.parse(entry.group(2));
                long sell = SurvivalItems.noFlatSell(material) ? 0 : Money.parse(entry.group(3));
                if (buy <= 0 || sell < 0) continue;
                Entry item = new Entry(currentId, material, buy, sell);
                table.byMaterial.put(material, item);
                currentItems.add(item);
            }
        }
        table.flushCategory(currentId, currentDisplay, currentIcon, currentItems);
        if (recipes != null && !recipes.isBlank()) table.applyRecipeSafety(recipes);
        return table;
    }

    /**
     * Find each item's least cost through all bundled vanilla recipes, then
     * limit the base sell price to half of that cost. The enchantment bonus is
     * capped below twice the base sell price, so even enchanted crafted items
     * cannot be sold for their ingredients' cost. Configuration remains on
     * disk unchanged; this safety also applies to pre-update/user price files.
     */
    private void applyRecipeSafety(String recipesText) {
        record Recipe(String output, int count, List<List<String>> ingredients) { }
        List<Recipe> recipes = new ArrayList<>();
        for (String line : recipesText.split("\n")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] parts = line.split("\\|", -1);
            if (parts.length != 3 || parts[0].isBlank()) continue;
            try {
                int count = Integer.parseInt(parts[1]);
                if (count <= 0) continue;
                List<List<String>> ingredients = new ArrayList<>();
                for (String group : parts[2].split(";")) ingredients.add(List.of(group.split(",")));
                if (!ingredients.isEmpty()) recipes.add(new Recipe(parts[0], count, ingredients));
            } catch (NumberFormatException ignored) { }
        }
        // Keep craftable intermediates in the graph even when an administrator
        // has removed them from the shop: players can still craft and use them.
        Map<String, Double> costs = new LinkedHashMap<>();
        byMaterial.forEach((material, entry) -> costs.put(material, (double) entry.buyCents()));
        for (int pass = 0; pass < 60; pass++) {
            boolean changed = false;
            for (Recipe recipe : recipes) {
                double cost = 0;
                for (List<String> alternatives : recipe.ingredients()) {
                    double cheapest = Double.POSITIVE_INFINITY;
                    for (String name : alternatives) cheapest = Math.min(cheapest,
                            costs.getOrDefault(name, Double.POSITIVE_INFINITY));
                    cost += cheapest;
                }
                cost /= recipe.count();
                if (cost + 1.0e-8 < costs.getOrDefault(recipe.output(), Double.POSITIVE_INFINITY)) {
                    costs.put(recipe.output(), cost);
                    changed = true;
                }
            }
            if (!changed) break;
        }
        Map<String, Entry> safe = new LinkedHashMap<>();
        byMaterial.forEach((name, entry) -> {
            long ceiling = (long) Math.floor(Math.min((double) entry.buyCents(), costs.get(name)) * 0.5 + 1.0e-8);
            long sell = SurvivalItems.noFlatSell(name) ? 0 : Math.max(0L, Math.min(entry.sellCents(), ceiling));
            safe.put(name, new Entry(entry.category(), name, entry.buyCents(), sell));
        });
        byMaterial.clear();
        byMaterial.putAll(safe);
        List<Category> updated = new ArrayList<>();
        for (Category category : categories) {
            List<Entry> items = new ArrayList<>();
            for (Entry entry : category.items()) items.add(safe.get(entry.material()));
            updated.add(new Category(category.id(), category.display(), category.icon(), Collections.unmodifiableList(items)));
        }
        categories.clear();
        categories.addAll(updated);
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
