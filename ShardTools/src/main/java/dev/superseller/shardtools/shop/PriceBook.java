package dev.superseller.shardtools.shop;

import java.util.HashMap;
import java.util.Map;

/**
 * Price resolution: catalog defaults plus runtime overrides (set via
 * /st setprice and persisted by the caller). Pure logic.
 */
public final class PriceBook {

    private final Map<String, Long> defaults = new HashMap<>();
    private final Map<String, Long> overrides = new HashMap<>();

    public PriceBook(Map<String, Long> defaultPrices, Map<String, Long> priceOverrides) {
        reload(defaultPrices, priceOverrides);
    }

    public void reload(Map<String, Long> defaultPrices, Map<String, Long> priceOverrides) {
        defaults.clear();
        if (defaultPrices != null) {
            defaults.putAll(defaultPrices);
        }
        overrides.clear();
        if (priceOverrides != null) {
            overrides.putAll(priceOverrides);
        }
    }

    /** Effective price or {@code null} if the item is unknown. */
    public Long price(String itemId) {
        Long overridden = overrides.get(itemId);
        if (overridden != null) {
            return overridden;
        }
        return defaults.get(itemId);
    }

    public boolean isOverridden(String itemId) {
        return overrides.containsKey(itemId);
    }

    /** Removes a runtime override, falling back to the configured default. */
    public void reset(String itemId) {
        overrides.remove(itemId);
    }

    public void setPrice(String itemId, long price) {
        overrides.put(itemId, Math.max(0L, price));
    }
}
