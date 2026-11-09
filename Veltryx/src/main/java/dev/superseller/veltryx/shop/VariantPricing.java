package dev.superseller.veltryx.shop;

/**
 * Approximate ingredient costs for the vanilla potion choices. Brewing uses
 * one ingredient for up to three bottles, and eight arrows use one lingering
 * potion. Fuel is deliberately omitted so the sale cap is conservative.
 * These caps reduce direct buy/brew/sell loops; they cannot model every farm,
 * villager trade, datapack, container return or third-party plugin.
 */
public final class VariantPricing {
    private VariantPricing() { }

    private static double cost(PriceTable prices, String material) {
        double value = prices.modeledCost(material);
        return Double.isFinite(value) && value > 0 ? value : 0;
    }
    private static double ingredient(PriceTable prices, String material) { return cost(prices, material) / 3.0; }
    private static double cheapestIngredient(PriceTable prices, String... materials) {
        double cheapest = Double.POSITIVE_INFINITY;
        for (String material : materials) cheapest = Math.min(cheapest, ingredient(prices, material));
        return cheapest;
    }

    /** Cheapest modeled cost of one chosen potion or tipped arrow, in cents. */
    public static double potionCost(PriceTable prices, String material, String key) {
        if (prices == null || key == null || ChoiceCatalog.potion(key) == null
                || !("POTION".equals(material) || "SPLASH_POTION".equals(material)
                || "LINGERING_POTION".equals(material) || "TIPPED_ARROW".equals(material))) return Double.NaN;
        String base = key;
        boolean extended = base.startsWith("LONG_");
        boolean strong = base.startsWith("STRONG_");
        if (extended) base = base.substring(5);
        if (strong) base = base.substring(7);
        double bottle = cost(prices, "GLASS_BOTTLE");
        double awkward = bottle + ingredient(prices, "NETHER_WART");
        double sugar = ingredient(prices, "SUGAR");
        double rabbit = ingredient(prices, "RABBIT_FOOT");
        double fermented = ingredient(prices, "FERMENTED_SPIDER_EYE");
        double poison = awkward + ingredient(prices, "SPIDER_EYE");
        double healing = awkward + ingredient(prices, "GLISTERING_MELON_SLICE");
        double result = switch (base) {
            case "WATER" -> bottle;
            // Every vanilla start-mix ingredient can also turn water mundane.
            case "MUNDANE" -> bottle + cheapestIngredient(prices, "REDSTONE", "BREEZE_ROD", "SLIME_BLOCK",
                    "STONE", "COBWEB", "MAGMA_CREAM", "RABBIT_FOOT", "SUGAR", "PUFFERFISH",
                    "GLISTERING_MELON_SLICE", "SPIDER_EYE", "GHAST_TEAR", "BLAZE_POWDER");
            case "THICK" -> bottle + ingredient(prices, "GLOWSTONE_DUST");
            case "AWKWARD" -> awkward;
            case "NIGHT_VISION" -> awkward + ingredient(prices, "GOLDEN_CARROT");
            case "INVISIBILITY" -> awkward + ingredient(prices, "GOLDEN_CARROT") + fermented;
            case "LEAPING" -> awkward + rabbit;
            case "FIRE_RESISTANCE" -> awkward + ingredient(prices, "MAGMA_CREAM");
            case "SWIFTNESS" -> awkward + sugar;
            case "SLOWNESS" -> awkward + Math.min(sugar, rabbit) + fermented;
            case "WATER_BREATHING" -> awkward + ingredient(prices, "PUFFERFISH");
            case "HEALING" -> healing;
            case "HARMING" -> Math.min(poison, healing) + fermented;
            case "POISON" -> poison;
            case "REGENERATION" -> awkward + ingredient(prices, "GHAST_TEAR");
            case "STRENGTH" -> awkward + ingredient(prices, "BLAZE_POWDER");
            case "WEAKNESS" -> bottle + fermented;
            case "TURTLE_MASTER" -> awkward + ingredient(prices, "TURTLE_HELMET");
            case "SLOW_FALLING" -> awkward + ingredient(prices, "PHANTOM_MEMBRANE");
            case "INFESTED" -> awkward + ingredient(prices, "STONE");
            case "OOZING" -> awkward + ingredient(prices, "SLIME_BLOCK");
            case "WEAVING" -> awkward + ingredient(prices, "COBWEB");
            case "WIND_CHARGED" -> awkward + ingredient(prices, "BREEZE_ROD");
            default -> Double.NaN;
        };
        if (extended) result += ingredient(prices, "REDSTONE");
        if (strong) result += ingredient(prices, "GLOWSTONE_DUST");
        if (material.equals("SPLASH_POTION") || material.equals("LINGERING_POTION") || material.equals("TIPPED_ARROW"))
            result += ingredient(prices, "GUNPOWDER");
        if (material.equals("LINGERING_POTION") || material.equals("TIPPED_ARROW")) {
            // The dragon-breath bottle is returned during brewing.
            result += Math.max(0, cost(prices, "DRAGON_BREATH") - bottle) / 3.0;
        }
        if (material.equals("TIPPED_ARROW")) result = (result + 8 * cost(prices, "ARROW")) / 8.0;
        return result;
    }

    /** Buy price includes a margin above the modeled ingredient cost. */
    public static long buyCents(PriceTable prices, String material, String key) {
        if (prices == null || prices.buyCents(material) <= 0) return -1;
        double cost = potionCost(prices, material, key);
        if (!Double.isFinite(cost) || cost < 0) return -1;
        double markedUp = Math.ceil(cost * 2.0);
        if (markedUp >= Long.MAX_VALUE) return -1;
        return Math.max(material.equals("TIPPED_ARROW") ? 10L : 20L, (long) markedUp);
    }

    /**
     * Sell at most half the modeled cost, and less than the selected buy price.
     * A disabled material stays disabled; lowering its configured base sell
     * proportionally lowers variant payouts, while higher values cannot bypass
     * the ingredient safety cap.
     */
    public static long sellCents(PriceTable prices, String material, String key) {
        if (prices == null) return 0;
        long configured = prices.sellCents(material);
        if (configured <= 0) return 0;
        double cost = potionCost(prices, material, key);
        long buy = buyCents(prices, material, key);
        if (!Double.isFinite(cost) || cost <= 0 || buy <= 1) return 0;
        long reference = material.equals("TIPPED_ARROW") ? 2L : 4L;
        double configuredFraction = Math.min(1.0, configured / (double) reference);
        long cap = (long) Math.min(Long.MAX_VALUE, Math.floor(cost * 0.5 * configuredFraction + 1.0e-8));
        return Math.max(0L, Math.min(cap, buy - 1));
    }
}
