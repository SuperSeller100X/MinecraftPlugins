package dev.superseller.veltryx.shop;

import dev.superseller.veltryx.util.Money;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Search the complete active catalogue using names, words, categories and base-price filters. */
public final class ShopSearch {
    private static final Pattern PRICE = Pattern.compile("^(buy|sell)(<=|>=|=|<|>)(.+)$");
    private record PriceFilter(boolean buy, String operator, BigInteger cents) {
        boolean matches(PriceTable.Entry entry) {
            BigInteger actual = BigInteger.valueOf(buy ? entry.buyCents() : entry.sellCents());
            int cmp = actual.compareTo(cents);
            return switch (operator) {
                case "<" -> cmp < 0;
                case "<=" -> cmp <= 0;
                case "=" -> cmp == 0;
                case ">=" -> cmp >= 0;
                case ">" -> cmp > 0;
                default -> false;
            };
        }
    }
    private ShopSearch() { }

    private static String words(String text) {
        return text.toLowerCase(Locale.ROOT).replace("minecraft:", "")
                .replace('_', ' ').replace('-', ' ').trim().replaceAll("\\s+", " ");
    }

    /**
     * Examples: {@code diamond sword}, {@code sword diamond},
     * {@code minecraft:diamond_sword}, {@code cat:tools buy<=50}, or {@code *}.
     * A malformed filter returns no results rather than silently broadening the search.
     * Price filters use configured/effective base prices; variant prices may differ.
     */
    public static List<PriceTable.Entry> find(PriceTable table, String query) {
        if (table == null || query == null || query.isBlank() || query.length() > 160) return List.of();
        List<String> terms = new ArrayList<>(), categories = new ArrayList<>();
        List<PriceFilter> filters = new ArrayList<>();
        for (String raw : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (raw.equals("*")) continue;
            if (raw.startsWith("cat:") || raw.startsWith("category:")) {
                String value = raw.substring(raw.indexOf(':') + 1);
                if (value.isBlank()) return List.of();
                categories.add(words(value));
                continue;
            }
            Matcher price = PRICE.matcher(raw);
            if (price.matches()) {
                BigInteger cents = Money.parseExact(price.group(3));
                if (cents == null) return List.of();
                filters.add(new PriceFilter(price.group(1).equals("buy"), price.group(2), cents));
                continue;
            }
            // A mistyped filter must not turn into an unexpected broad query.
            if (raw.matches("^(buy|sell)[<>=].*")) return List.of();
            String normalized = words(raw);
            if (!normalized.isEmpty()) for (String term : normalized.split(" ")) terms.add(term);
        }
        List<PriceTable.Entry> found = new ArrayList<>();
        for (PriceTable.Category category : table.categories()) {
            String categoryText = words(category.id() + " " + category.display());
            if (categories.stream().anyMatch(filter -> !categoryText.contains(filter))) continue;
            for (PriceTable.Entry entry : category.items()) {
                String itemText = words(entry.material() + " " + PriceTable.displayName(entry.material()));
                if (terms.stream().anyMatch(term -> !itemText.contains(term))) continue;
                if (filters.stream().anyMatch(filter -> !filter.matches(entry))) continue;
                found.add(entry);
            }
        }
        String joined = String.join(" ", terms);
        found.sort(Comparator.<PriceTable.Entry>comparingInt(entry -> {
            String name = words(entry.material());
            if (joined.isEmpty()) return 0;
            if (name.equals(joined)) return 0;
            if (name.startsWith(joined)) return 1;
            return 2;
        }).thenComparing(PriceTable.Entry::material));
        return List.copyOf(found);
    }
}
