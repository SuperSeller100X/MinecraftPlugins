package dev.superseller.shardtools.item;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Vanilla enchantment incompatibility groups. When the user toggles one
 * enchantment on, every enchantment in the same group is automatically
 * deselected so the result is always a legal combination.
 *
 * The groups mirror vanilla Minecraft: two enchantments from the same group
 * cannot coexist on a single item. Enchantments that are not listed here are
 * assumed to be compatible with everything.
 */
public final class IncompatibilityMap {

    /** Enchant id -> set of other enchant ids it conflicts with. */
    private static final Map<String, Set<String>> CONFLICTS;

    static {
        Map<String, Set<String>> map = new HashMap<>();

        // Armor protection types (only one can be on a piece of armor)
        addGroup(map, "protection", "blast_protection", "fire_protection", "projectile_protection");

        // Boots: depth_strider and frost_walker
        addGroup(map, "depth_strider", "frost_walker");

        // Sword damage types
        addGroup(map, "sharpness", "smite", "bane_of_arthropods");

        // Tool fortune vs silk touch
        addGroup(map, "fortune", "silk_touch");

        // Bow: mending and infinity
        addGroup(map, "mending", "infinity");

        // Crossbow: multishot and piercing
        addGroup(map, "multishot", "piercing");

        // Trident: loyalty and riptide
        addGroup(map, "loyalty", "riptide");

        // Trident: channeling and riptide
        addGroup(map, "channeling", "riptide");

        CONFLICTS = Collections.unmodifiableMap(map);
    }

    private IncompatibilityMap() {
    }

    /**
     * Returns every enchant id that conflicts with {@code enchantId}
     * (not including {@code enchantId} itself), or an empty set when the
     * enchantment has no known conflicts.
     */
    public static Set<String> conflicts(String enchantId) {
        Set<String> found = CONFLICTS.get(enchantId);
        return found != null ? found : Collections.emptySet();
    }

    /** True when two enchantments cannot coexist on the same item. */
    public static boolean incompatible(String a, String b) {
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        Set<String> set = CONFLICTS.get(a);
        return set != null && set.contains(b);
    }

    /**
     * Removes every enchant id in {@code current} that conflicts with
     * {@code justEnabled}. Modifies {@code current} in place and returns it
     * for convenience.
     */
    public static Set<String> removeConflicting(Set<String> current, String justEnabled) {
        Set<String> conflicting = CONFLICTS.get(justEnabled);
        if (conflicting != null) {
            current.removeAll(conflicting);
        }
        return current;
    }

    private static void addGroup(Map<String, Set<String>> map, String... ids) {
        for (String id : ids) {
            Set<String> others = map.computeIfAbsent(id, k -> new HashSet<>());
            for (String other : ids) {
                if (!other.equals(id)) {
                    others.add(other);
                }
            }
        }
    }
}