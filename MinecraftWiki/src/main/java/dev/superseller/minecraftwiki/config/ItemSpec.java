package dev.superseller.minecraftwiki.config;

import java.util.List;

import org.bukkit.Material;

/**
 * A fully configurable GUI icon: material, MiniMessage display name, MiniMessage lore
 * lines and an optional enchantment glint.
 */
public record ItemSpec(Material material, String name, List<String> lore, boolean glowing) {

    public ItemSpec {
        material = material == null ? Material.PAPER : material;
        name = name == null ? "" : name;
        lore = lore == null ? List.of() : List.copyOf(lore);
    }

    public static ItemSpec of(Material material, String name, List<String> lore) {
        return new ItemSpec(material, name, lore, false);
    }

    public static ItemSpec plain(Material material, String name) {
        return new ItemSpec(material, name, List.of(), false);
    }

    /** Returns a copy that draws a different material, keeping the configured text. */
    public ItemSpec withMaterial(Material replacement) {
        return replacement == null || replacement == material
                ? this
                : new ItemSpec(replacement, name, lore, glowing);
    }

    /** True when the spec carries no display name at all (used for border filler). */

    public boolean unnamed() {
        return name.isEmpty();
    }
}
