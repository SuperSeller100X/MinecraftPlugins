package dev.superseller.minecraftwiki.provider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One immutable fact sheet captured from a registry entry.
 *
 * <p>Properties are already-formatted strings keyed by a stable machine key such as
 * {@code max_level}. The renderer maps those keys to display labels from {@code gui.yml},
 * so no provider hardcodes wording.</p>
 *
 * @param key        namespaced key without the {@code minecraft:} namespace
 * @param label      display label, normally the prettified key
 * @param properties ordered fact sheet, may be empty
 */
public record RegistryEntry(String key, String label, Map<String, String> properties) {

    public RegistryEntry {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    /** Builder for the ordered property map. */
    public static final class Facts {

        private final LinkedHashMap<String, String> values = new LinkedHashMap<>();

        public Facts put(String key, String value) {
            if (key != null && value != null && !value.isEmpty()) {
                values.put(key, value);
            }
            return this;
        }

        /** Adds the property only when the condition is true, keeping fact sheets readable. */
        public Facts when(String key, String value, boolean condition) {
            return condition ? put(key, value) : this;
        }

        public Facts number(String key, double value) {
            return put(key, trim(value));
        }

        public boolean isEmpty() {
            return values.isEmpty();
        }

        public Map<String, String> build() {
            return values;
        }

        /** Renders a double without a pointless {@code .0} tail. */
        public static String trim(double value) {
            if (value == Math.rint(value) && Math.abs(value) < 1e15) {
                return Long.toString((long) value);
            }
            return Double.toString(value);
        }
    }
}
