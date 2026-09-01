package dev.superseller.shardtools.item;

import java.util.Locale;

/**
 * Enchant id + level pair parsed from "efficiency:5" style config strings.
 */
public final class EnchantSpec {

    private final String enchant;
    private final int level;

    public EnchantSpec(String enchant, int level) {
        this.enchant = enchant;
        this.level = level;
    }

    public String enchant() {
        return enchant;
    }

    public int level() {
        return level;
    }

    public static EnchantSpec parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        int colon = trimmed.indexOf(':');
        String name;
        int level = 1;
        if (colon > 0) {
            name = trimmed.substring(0, colon).trim();
            try {
                level = Integer.parseInt(trimmed.substring(colon + 1).trim());
            } catch (NumberFormatException ignored) {
                level = 1;
            }
        } else {
            name = trimmed;
        }
        return new EnchantSpec(name.toLowerCase(Locale.ROOT).replace("minecraft:", ""), Math.max(1, level));
    }
}
