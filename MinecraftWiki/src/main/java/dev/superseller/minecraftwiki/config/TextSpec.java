package dev.superseller.minecraftwiki.config;

import java.util.List;

/**
 * Configurable MiniMessage name and lore without a material, for icons whose material
 * comes from the data being displayed (categories, article entries).
 */
public record TextSpec(String name, List<String> lore) {

    public TextSpec {
        name = name == null ? "" : name;
        lore = lore == null ? List.of() : List.copyOf(lore);
    }
}
