package dev.superseller.shardtools.item;

/**
 * Special behaviour attached to a catalog item.
 */
public enum Behavior {
    NONE,
    /** Pickaxe that mines a 3x3 (configurable radius) plane. */
    AREA_PICKAXE,
    /** Shovel that digs a 3x3 plane. */
    AREA_SHOVEL,
    /** Axe that fells whole trees. */
    TREE_AXE,
    /** Drinkable potion granting haste. */
    HASTE_POTION;

    public static Behavior parse(String text) {
        if (text == null) {
            return NONE;
        }
        try {
            return valueOf(text.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
