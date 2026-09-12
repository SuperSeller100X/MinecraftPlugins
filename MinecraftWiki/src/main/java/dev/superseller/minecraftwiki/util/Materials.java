package dev.superseller.minecraftwiki.util;

import org.bukkit.Material;

/**
 * Resolves configured material names to {@link Material} values.
 *
 * <p>Configuration mistakes must be loud, never silent: {@link #resolve} returns the
 * match plus the reason it failed so the config validator can report it, and callers
 * always fall back to an explicit default.</p>
 */
public final class Materials {

    private Materials() {
    }

    /** Result of a material lookup. */
    public record Lookup(Material material, String problem) {
        public boolean ok() {
            return material != null;
        }
    }

    /** Resolves a configured name such as {@code DIAMOND} or {@code minecraft:diamond}. */
    public static Lookup resolve(String configured, Material fallback, String context) {
        if (configured == null || configured.isBlank()) {
            return new Lookup(fallback, context + ": no material configured, using " + fallback.name());
        }
        Material material;
        try {
            material = Material.matchMaterial(configured.trim());
        } catch (Throwable error) {
            material = null;
        }
        if (material == null) {
            return new Lookup(fallback, context + ": unknown material '" + configured + "', using " + fallback.name());
        }
        if (material.isLegacy()) {
            return new Lookup(material, context + ": '" + configured + "' is a legacy material id");
        }
        if (!material.isItem()) {
            return new Lookup(material, context + ": '" + configured + "' is a block without an item form");
        }
        return new Lookup(material, null);
    }

    /** Resolves and logs nothing; used where the caller reports problems itself. */
    public static Material orDefault(String configured, Material fallback) {
        return resolve(configured, fallback, "").material();
    }
}
